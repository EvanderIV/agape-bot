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
 * <p>The detector loads the standard public-domain OpenCV cascade
 * ({@code assets/haarcascade_frontalface_default.xml}, a stump-based 24x24
 * detector) with the JDK's built-in XML parser — no native libraries or extra
 * Maven dependencies. The cascade is parsed once and cached.
 *
 * <p>Entry point: {@link #computeFocus(String)} returns a normalized focal
 * point {@code {x, y}} in [0,1] (relative to the full image), or {@code null}
 * when no face is found or anything goes wrong. Callers treat {@code null} as
 * "keep the default centered crop" — detection is best-effort and never throws.
 */
public class FaceDetector {

    private static final String CASCADE_PATH = "assets/haarcascade_frontalface_default.xml";

    /** Longest side the image is downscaled to before detection (speed vs. accuracy). */
    private static final int MAX_WORKING_DIM = 384;
    /** Window grows by this factor each pyramid step. */
    private static final double SCALE_FACTOR = 1.2;
    /** A grouped cluster needs at least this many raw hits to count as a face. */
    private static final int MIN_NEIGHBORS = 3;

    private static volatile Cascade cascade;   // lazily loaded, then cached
    private static volatile boolean loadFailed; // don't retry a broken/missing cascade every call

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

    /** A depth-1 decision stump (the default cascade is entirely stumps). */
    private static final class Stump {
        final int featureIdx;
        final double threshold;
        final double leftVal;
        final double rightVal;
        Stump(int featureIdx, double threshold, double leftVal, double rightVal) {
            this.featureIdx = featureIdx;
            this.threshold = threshold;
            this.leftVal = leftVal;
            this.rightVal = rightVal;
        }
    }

    private static final class Stage {
        final double threshold;
        final Stump[] stumps;
        Stage(double threshold, Stump[] stumps) { this.threshold = threshold; this.stumps = stumps; }
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
     *         {@code null} if no face was found or the photo can't be analyzed
     */
    public static float[] computeFocus(String photoPath) {
        if (photoPath == null || photoPath.isEmpty()) return null;
        if (photoPath.startsWith("http")) return null; // Discord avatars/URLs: leave centered
        File file = new File(photoPath);
        if (!file.isFile()) return null;

        try {
            Cascade c = getCascade();
            if (c == null) return null;

            BufferedImage src = ImageIO.read(file);
            if (src == null) return null;

            // Downscale for speed; the focal point is normalized so scale doesn't matter.
            int srcW = src.getWidth();
            int srcH = src.getHeight();
            if (srcW < c.width || srcH < c.height) return null;

            double downscale = Math.min(1.0, (double) MAX_WORKING_DIM / Math.max(srcW, srcH));
            int workW = Math.max(c.width, (int) Math.round(srcW * downscale));
            int workH = Math.max(c.height, (int) Math.round(srcH * downscale));

            int[][] gray = toGrayscale(src, workW, workH);
            long[][] ii = integral(gray, false);
            long[][] ii2 = integral(gray, true);

            List<int[]> hits = detect(c, ii, ii2, workW, workH);
            int[] face = pickBestFace(hits);
            if (face == null) return null;

            // face = {x, y, size}; use its center, normalized to [0,1].
            float fx = (float) ((face[0] + face[2] / 2.0) / workW);
            float fy = (float) ((face[1] + face[2] / 2.0) / workH);
            fx = clamp01(fx);
            fy = clamp01(fy);
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
            int step = Math.max(1, (int) Math.round(scale * 1.5));

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
            for (Stump stump : stage.stumps) {
                Feature f = c.features[stump.featureIdx];
                double featVal = featureSum(ii, f, x, y, scale) * invArea;
                stageSum += (featVal < stump.threshold * stddev) ? stump.leftVal : stump.rightVal;
            }
            if (stageSum < stage.threshold) return false;
        }
        return true;
    }

    /**
     * Computes a Haar feature's response over a window. The first rectangle's
     * weight is recomputed per scale so the feature has zero DC response (the
     * same normalization OpenCV applies), which keeps detection stable across
     * the image pyramid.
     */
    private static double featureSum(long[][] ii, Feature f, int winX, int winY, double scale) {
        HaarRect[] rects = f.rects;

        // Scaled geometry of each rectangle within the window.
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
     * Clusters overlapping raw detections, keeps clusters with enough members
     * (rejecting one-off false positives), and returns the largest surviving
     * face as {x, y, size}, or null if none qualify.
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

        int[] best = null;
        int bestSize = -1;
        for (int g = 0; g < groupCount; g++) {
            if (members[g] < MIN_NEIGHBORS) continue;
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
        return best;
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

    private static Cascade getCascade() {
        Cascade local = cascade;
        if (local != null) return local;
        if (loadFailed) return null;
        synchronized (FaceDetector.class) {
            if (cascade != null) return cascade;
            if (loadFailed) return null;
            try {
                cascade = parseCascade(new File(CASCADE_PATH));
            } catch (Throwable t) {
                loadFailed = true;
                System.err.println("FaceDetector: could not load cascade at " + CASCADE_PATH
                        + " — face-centered cropping disabled (" + t.getMessage() + ")");
            }
            return cascade;
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
            Stump[] stumps = new Stump[weaks.size()];
            for (int wci = 0; wci < weaks.size(); wci++) {
                double[] inodes = parseDoubles(text(firstChild(weaks.get(wci), "internalNodes")));
                double[] leaves = parseDoubles(text(firstChild(weaks.get(wci), "leafValues")));
                int featureIdx = (int) inodes[2];
                double threshold = inodes[3];
                stumps[wci] = new Stump(featureIdx, threshold, leaves[0], leaves[1]);
            }
            stages[s] = new Stage(stageThreshold, stumps);
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
