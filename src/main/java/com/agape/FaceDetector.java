package com.agape;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java frontal-face detector (Viola-Jones / Haar cascade), used at photo
 * upload time to find where a face sits in an applicant's picture so the
 * profile-card renderer can crop toward it instead of blindly center-cropping.
 *
 * <p>It loads the standard public-domain OpenCV cascades with the JDK's built-in
 * XML parser — no native libraries or extra Maven dependencies — and caches them:
 * <ul>
 *   <li>{@code haarcascade_frontalface_default.xml} (24x24, decision stumps) —
 *       fast primary pass.</li>
 *   <li>{@code haarcascade_frontalface_alt2.xml} (20x20, depth-2 CART trees) —
 *       a complementary, more sensitive fallback run only when the primary
 *       finds nothing. It catches many faces the default misses (glasses,
 *       tilt, partial occlusion, low contrast).</li>
 * </ul>
 *
 * <p>To squeeze recall out of hard photos (dim mirror selfies, darker skin,
 * small faces in tall frames) the working image is histogram-equalized before
 * detection, scanned at a fine scale/step, and grouped with a graduated
 * neighbor threshold. As a last resort, a clearly portrait-oriented photo with
 * no detected face is biased toward the upper third (where selfie faces sit)
 * rather than dead center; landscape/square photos just center on a miss.
 *
 * <p>Entry point: {@link #computeFocus(String)} returns a normalized focal
 * point {@code {x, y}} in [0,1] (relative to the full image), or {@code null}
 * when nothing usable is found. Callers treat {@code null} as "keep the default
 * centered crop" — detection is best-effort and never throws.
 */
public class FaceDetector {

    private static final String[] CASCADE_PATHS = {
        "assets/haarcascade_frontalface_default.xml",
        "assets/haarcascade_frontalface_alt2.xml"
    };

    /** Longest side the image is downscaled to before detection (higher = catches smaller faces, slower). */
    private static final int MAX_WORKING_DIM = 384;
    /** Window grows by this factor each pyramid step (smaller = finer = more sensitive). */
    private static final double SCALE_FACTOR = 1.2;
    /** Window-shift factor per scale (smaller = denser scan = more sensitive). */
    private static final double STEP_FACTOR = 1.5;
    /**
     * Minimum overlapping detections required to accept a face cluster. Kept
     * strict (3) on purpose: relaxing to 2 lets textured backgrounds (grass,
     * tile) form spurious 2-hit clusters, and a wrong crop is worse than a miss.
     * Recall instead comes from equalization + the alt2 cascade getting real
     * faces well above 3, and genuine misses fall back to the portrait
     * upper-third. (Kept as an array so the threshold can be graduated later if
     * a safe lower level is found.)
     */
    private static final int[] MIN_NEIGHBOR_LEVELS = { 3 };
    /** Where a no-face PORTRAIT photo is focused (upper third, where selfie faces sit). */
    private static final float PORTRAIT_FALLBACK_Y = 0.33f;

    private static volatile Cascade[] cascades;    // lazily loaded, then cached
    private static volatile boolean loadFailed;    // don't retry broken/missing cascades every call

    // ------------------------------------------------------------------
    // Cascade model
    // ------------------------------------------------------------------

    private static final class HaarRect {
        final int x, y, w, h;
        final double weight;
        HaarRect(int x, int y, int w, int h, double weight) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.weight = weight;
        }
    }

    private static final class Feature {
        final HaarRect[] rects;
        Feature(HaarRect[] rects) { this.rects = rects; }
    }

    /**
     * One node of a CART tree. {@code left}/{@code right} are the next node index
     * when {@code > 0}, or a leaf index ({@code -value}) when {@code <= 0}.
     */
    private static final class TreeNode {
        final int left, right, featureIdx;
        final double threshold;
        TreeNode(int left, int right, int featureIdx, double threshold) {
            this.left = left; this.right = right; this.featureIdx = featureIdx; this.threshold = threshold;
        }
    }

    /** A weak classifier: a small CART tree (depth 1 for stumps, deeper for alt2). */
    private static final class WeakClassifier {
        final TreeNode[] nodes;
        final double[] leaves;
        WeakClassifier(TreeNode[] nodes, double[] leaves) { this.nodes = nodes; this.leaves = leaves; }
    }

    private static final class Stage {
        final double threshold;
        final WeakClassifier[] weaks;
        Stage(double threshold, WeakClassifier[] weaks) { this.threshold = threshold; this.weaks = weaks; }
    }

    private static final class Cascade {
        final int width, height;
        final Stage[] stages;
        final Feature[] features;
        Cascade(int width, int height, Stage[] stages, Feature[] features) {
            this.width = width; this.height = height; this.stages = stages; this.features = features;
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Detects the most prominent face in the given image and returns its center
     * as a normalized focal point.
     *
     * @param photoPath a local image file path (http URLs and the bundled
     *                  placeholder avatars are ignored)
     * @return {@code {focusX, focusY}} in [0,1] relative to the full image, or
     *         {@code null} if nothing usable was found
     */
    public static float[] computeFocus(String photoPath) {
        if (photoPath == null || photoPath.isEmpty()) return null;
        if (photoPath.startsWith("http")) return null; // Discord avatars/URLs: leave centered
        File file = new File(photoPath);
        if (!file.isFile()) return null;

        try {
            Cascade[] cs = getCascades();
            if (cs == null || cs.length == 0) return null;

            BufferedImage src = ImageIO.read(file);
            if (src == null) return null;

            int srcW = src.getWidth();
            int srcH = src.getHeight();
            int minCascade = 24;
            if (srcW < minCascade || srcH < minCascade) return null;

            // Downscale for speed; the focal point is normalized so scale doesn't matter.
            double downscale = Math.min(1.0, (double) MAX_WORKING_DIM / Math.max(srcW, srcH));
            int workW = Math.max(minCascade, (int) Math.round(srcW * downscale));
            int workH = Math.max(minCascade, (int) Math.round(srcH * downscale));

            int[][] gray = toGrayscale(src, workW, workH);
            equalizeHistogram(gray); // boost contrast — big help for dim / low-contrast / darker-skin faces
            long[][] ii = integral(gray, false);
            long[][] ii2 = integral(gray, true);

            // Try each cascade in order; the first to find a face wins (default is
            // fast, alt2 is the sensitive fallback).
            int[] face = null;
            for (Cascade c : cs) {
                List<int[]> hits = detect(c, ii, ii2, workW, workH);
                face = pickBestFace(hits);
                if (face != null) break;
            }

            if (face == null) {
                // No detection. Only PORTRAIT photos (taller than wide) get the
                // upper-third bias, where selfie faces sit instead of the torso.
                // Landscape/square photos just center (return null).
                if (workH > workW) {
                    return new float[] { 0.5f, PORTRAIT_FALLBACK_Y };
                }
                return null;
            }

            // face = {x, y, size}; use its center, normalized to [0,1].
            float fx = clamp01((float) ((face[0] + face[2] / 2.0) / workW));
            float fy = clamp01((float) ((face[1] + face[2] / 2.0) / workH));
            return new float[] { fx, fy };
        } catch (Throwable t) {
            // Best-effort only: a detector hiccup must never break the application flow.
            System.err.println("FaceDetector: detection failed for " + photoPath + " — " + t.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /** Slides the cascade window across an image pyramid; returns raw {x,y,size} passes. */
    private static List<int[]> detect(Cascade c, long[][] ii, long[][] ii2, int w, int h) {
        List<int[]> hits = new ArrayList<>();
        double maxScale = Math.min((double) w / c.width, (double) h / c.height);

        for (double scale = 1.0; scale <= maxScale; scale *= SCALE_FACTOR) {
            int win = (int) Math.round(c.width * scale);
            if (win < c.width) win = c.width;
            int step = Math.max(1, (int) Math.round(scale * STEP_FACTOR));

            for (int y = 0; y + win < h; y += step) {
                for (int x = 0; x + win < w; x += step) {
                    if (passes(c, ii, ii2, x, y, scale, win)) {
                        hits.add(new int[] { x, y, win });
                    }
                }
            }
        }
        return hits;
    }

    /** Evaluates every cascade stage on one window, with stddev variance normalization. */
    private static boolean passes(Cascade c, long[][] ii, long[][] ii2, int x, int y, double scale, int win) {
        double invArea = 1.0 / ((double) win * win);
        double sum = rectSum(ii, x, y, win, win);
        double sqSum = rectSum(ii2, x, y, win, win);
        double mean = sum * invArea;
        double variance = sqSum * invArea - mean * mean;
        double stddev = variance > 1.0 ? Math.sqrt(variance) : 1.0;

        for (Stage stage : c.stages) {
            double stageSum = 0.0;
            for (WeakClassifier wc : stage.weaks) {
                stageSum += evalTree(wc, c, ii, x, y, scale, invArea, stddev);
            }
            if (stageSum < stage.threshold) return false;
        }
        return true;
    }

    /**
     * Walks a weak classifier's CART tree, branching on variance-normalized Haar
     * feature responses, and returns the reached leaf value. Handles both stumps
     * (default cascade) and deeper trees (alt2).
     */
    private static double evalTree(WeakClassifier wc, Cascade c, long[][] ii,
            int x, int y, double scale, double invArea, double stddev) {
        int nodeIdx = 0;
        while (true) {
            TreeNode node = wc.nodes[nodeIdx];
            double featVal = featureSum(ii, c.features[node.featureIdx], x, y, scale) * invArea;
            int branch = (featVal < node.threshold * stddev) ? node.left : node.right;
            if (branch <= 0) return wc.leaves[-branch]; // <= 0 encodes a leaf index
            nodeIdx = branch;
        }
    }

    /**
     * Computes a Haar feature's response over a window. The first rectangle's
     * weight is recomputed per scale so the feature has zero DC response (the
     * same normalization OpenCV applies), which keeps detection stable across
     * the image pyramid.
     */
    private static double featureSum(long[][] ii, Feature f, int winX, int winY, double scale) {
        HaarRect[] rects = f.rects;

        int n = rects.length;
        long[] rectSums = new long[n];
        long[] areas = new long[n];
        for (int i = 0; i < n; i++) {
            HaarRect r = rects[i];
            int rx = winX + (int) Math.round(r.x * scale);
            int ry = winY + (int) Math.round(r.y * scale);
            int rw = (int) Math.round(r.w * scale);
            int rh = (int) Math.round(r.h * scale);
            rectSums[i] = rectSum(ii, rx, ry, rw, rh);
            areas[i] = (long) rw * rh;
        }

        // Re-derive weight[0] so the weighted areas cancel to zero (DC normalization).
        double restWeightedArea = 0.0;
        for (int i = 1; i < n; i++) restWeightedArea += rects[i].weight * areas[i];
        double weight0 = areas[0] != 0 ? -restWeightedArea / areas[0] : rects[0].weight;

        double total = weight0 * rectSums[0];
        for (int i = 1; i < n; i++) total += rects[i].weight * rectSums[i];
        return total;
    }

    /** Sum of pixels in [x, x+w) x [y, y+h) via a padded integral image. */
    private static long rectSum(long[][] integral, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        return integral[y2][x2] - integral[y][x2] - integral[y2][x] + integral[y][x];
    }

    // ------------------------------------------------------------------
    // Grouping
    // ------------------------------------------------------------------

    /**
     * Clusters overlapping raw detections and returns the largest cluster as
     * {x, y, size}. Uses a graduated neighbor threshold: prefers clusters with 3+
     * members (robust), but relaxes to 2 then 1 so a weak-but-real detection on a
     * hard photo still wins rather than being discarded.
     */
    private static int[] pickBestFace(List<int[]> hits) {
        if (hits.isEmpty()) return null;

        int n = hits.size();
        int[] label = new int[n];
        for (int i = 0; i < n; i++) label[i] = -1;
        int groupCount = 0;

        // Union overlapping rects into groups (single pass with transitive merge).
        for (int i = 0; i < n; i++) {
            if (label[i] == -1) label[i] = groupCount++;
            for (int j = i + 1; j < n; j++) {
                if (similar(hits.get(i), hits.get(j))) {
                    if (label[j] == -1) {
                        label[j] = label[i];
                    } else if (label[j] != label[i]) {
                        int from = label[j], to = label[i];
                        for (int k = 0; k < n; k++) if (label[k] == from) label[k] = to;
                    }
                }
            }
        }

        // Average each group and count its members.
        double[] sx = new double[groupCount];
        double[] sy = new double[groupCount];
        double[] ss = new double[groupCount];
        int[] members = new int[groupCount];
        for (int i = 0; i < n; i++) {
            int g = label[i];
            sx[g] += hits.get(i)[0];
            sy[g] += hits.get(i)[1];
            ss[g] += hits.get(i)[2];
            members[g]++;
        }

        // Prefer well-supported clusters; relax the threshold only if nothing qualifies.
        for (int minNeighbors : MIN_NEIGHBOR_LEVELS) {
            int[] best = null;
            int bestSize = -1;
            for (int g = 0; g < groupCount; g++) {
                if (members[g] < minNeighbors) continue;
                int avgSize = (int) Math.round(ss[g] / members[g]);
                if (avgSize > bestSize) { // prefer the largest (most prominent) face
                    bestSize = avgSize;
                    best = new int[] {
                        (int) Math.round(sx[g] / members[g]),
                        (int) Math.round(sy[g] / members[g]),
                        avgSize
                    };
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Two detections belong together if their positions and sizes are close. */
    private static boolean similar(int[] a, int[] b) {
        double eps = 0.2;
        double delta = eps * (a[2] + b[2]) / 2.0;
        return Math.abs(a[0] - b[0]) <= delta
            && Math.abs(a[1] - b[1]) <= delta
            && Math.abs(a[2] - b[2]) <= 0.3 * (a[2] + b[2]) / 2.0;
    }

    // ------------------------------------------------------------------
    // Image helpers
    // ------------------------------------------------------------------

    private static int[][] toGrayscale(BufferedImage src, int w, int h) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = (int) Math.round(0.299 * r + 0.587 * gg + 0.114 * b);
            }
        }
        return gray;
    }

    /**
     * Global histogram equalization (in place). Spreads the intensity range so
     * faces in dim, flat, or low-contrast photos expose enough edge structure for
     * the Haar features to fire.
     */
    private static void equalizeHistogram(int[][] gray) {
        int h = gray.length;
        int w = gray[0].length;
        int total = w * h;
        if (total == 0) return;

        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[gray[y][x] & 0xFF]++;

        int[] lut = new int[256];
        long cum = 0;
        long cdfMin = -1;
        for (int i = 0; i < 256; i++) {
            cum += hist[i];
            if (cum == 0) continue;
            if (cdfMin < 0) cdfMin = cum; // first non-empty bin
            long denom = total - cdfMin;
            lut[i] = denom <= 0 ? i : (int) Math.round((double) (cum - cdfMin) / denom * 255.0);
            if (lut[i] < 0) lut[i] = 0;
            if (lut[i] > 255) lut[i] = 255;
        }

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                gray[y][x] = lut[gray[y][x] & 0xFF];
    }

    /** Builds a padded (h+1 x w+1) integral image; squared pixels when {@code squared}. */
    private static long[][] integral(int[][] gray, boolean squared) {
        int h = gray.length;
        int w = gray[0].length;
        long[][] ii = new long[h + 1][w + 1];
        for (int y = 0; y < h; y++) {
            long rowSum = 0;
            for (int x = 0; x < w; x++) {
                long v = gray[y][x];
                if (squared) v *= v;
                rowSum += v;
                ii[y + 1][x + 1] = ii[y][x + 1] + rowSum;
            }
        }
        return ii;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    // ------------------------------------------------------------------
    // Cascade loading
    // ------------------------------------------------------------------

    private static Cascade[] getCascades() {
        Cascade[] local = cascades;
        if (local != null) return local;
        if (loadFailed) return null;
        synchronized (FaceDetector.class) {
            if (cascades != null) return cascades;
            if (loadFailed) return null;
            List<Cascade> loaded = new ArrayList<>();
            for (String path : CASCADE_PATHS) {
                File f = new File(path);
                if (!f.isFile()) {
                    System.err.println("FaceDetector: cascade not found at " + path + " — skipping it");
                    continue;
                }
                try {
                    loaded.add(parseCascade(f));
                } catch (Throwable t) {
                    System.err.println("FaceDetector: failed to parse cascade " + path + " — " + t.getMessage());
                }
            }
            if (loaded.isEmpty()) {
                loadFailed = true;
                System.err.println("FaceDetector: no usable cascades — face-centered cropping disabled");
                return null;
            }
            cascades = loaded.toArray(new Cascade[0]);
            return cascades;
        }
    }

    private static Cascade parseCascade(File xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Element root = db.parse(xml).getDocumentElement();

        Element cascadeEl = firstByTag(root, "cascade");
        if (cascadeEl == null) cascadeEl = root; // some exports omit the wrapper
        int width = Integer.parseInt(text(firstChild(cascadeEl, "width")).trim());
        int height = Integer.parseInt(text(firstChild(cascadeEl, "height")).trim());

        // Stages
        Element stagesEl = firstChild(cascadeEl, "stages");
        List<Element> stageEls = childrenByTag(stagesEl, "_");
        Stage[] stages = new Stage[stageEls.size()];
        for (int s = 0; s < stageEls.size(); s++) {
            Element stageEl = stageEls.get(s);
            double stageThreshold = Double.parseDouble(text(firstChild(stageEl, "stageThreshold")).trim());
            Element weakEl = firstChild(stageEl, "weakClassifiers");
            List<Element> weaks = childrenByTag(weakEl, "_");
            WeakClassifier[] classifiers = new WeakClassifier[weaks.size()];
            for (int wci = 0; wci < weaks.size(); wci++) {
                double[] inodes = parseDoubles(text(firstChild(weaks.get(wci), "internalNodes")));
                double[] leaves = parseDoubles(text(firstChild(weaks.get(wci), "leafValues")));
                // internalNodes is a flat list of [left, right, featureIdx, threshold] per node.
                int nodeCount = inodes.length / 4;
                TreeNode[] nodes = new TreeNode[nodeCount];
                for (int ni = 0; ni < nodeCount; ni++) {
                    int base = ni * 4;
                    nodes[ni] = new TreeNode(
                        (int) inodes[base],       // left
                        (int) inodes[base + 1],   // right
                        (int) inodes[base + 2],   // feature index
                        inodes[base + 3]);        // threshold
                }
                classifiers[wci] = new WeakClassifier(nodes, leaves);
            }
            stages[s] = new Stage(stageThreshold, classifiers);
        }

        // Features
        Element featuresEl = firstChild(cascadeEl, "features");
        List<Element> featEls = childrenByTag(featuresEl, "_");
        Feature[] features = new Feature[featEls.size()];
        for (int fi = 0; fi < featEls.size(); fi++) {
            Element rectsEl = firstChild(featEls.get(fi), "rects");
            List<Element> rectEls = childrenByTag(rectsEl, "_");
            HaarRect[] rects = new HaarRect[rectEls.size()];
            for (int ri = 0; ri < rectEls.size(); ri++) {
                double[] v = parseDoubles(text(rectEls.get(ri)));
                rects[ri] = new HaarRect((int) v[0], (int) v[1], (int) v[2], (int) v[3], v[4]);
            }
            features[fi] = new Feature(rects);
        }

        return new Cascade(width, height, stages, features);
    }

    // --- tiny DOM helpers (avoid getElementsByTagName, which matches all "_" descendants) ---

    private static Element firstByTag(Element parent, String name) {
        NodeList list = parent.getElementsByTagName(name);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static Element firstChild(Element parent, String name) {
        if (parent == null) return null;
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node node = kids.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> childrenByTag(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        if (parent == null) return out;
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node node = kids.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                out.add((Element) node);
            }
        }
        return out;
    }

    private static String text(Element el) {
        return el == null ? "" : el.getTextContent();
    }

    private static double[] parseDoubles(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new double[0];
        String[] parts = trimmed.split("\\s+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }
}
