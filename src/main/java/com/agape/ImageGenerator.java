package com.agape;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.awt.geom.AffineTransform;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.emoji.EmojiParser;

public class ImageGenerator {

    static class FontLoader {
        private static final Map<String, Font> cache = new HashMap<>();

        public static Font getFont(String fontPath, float size) {
            if (fontPath == null || fontPath.isEmpty()) {
                return new Font("Arial Rounded MT Bold", Font.BOLD, (int) size);
            }
            if (!cache.containsKey(fontPath)) {
                try {
                    File fontFile = new File(fontPath);
                    if (!fontFile.exists()) {
                        // If it doesn't look like a file (no extension), try loading it as a system
                        // font
                        if (!fontPath.contains(".")) {
                            cache.put(fontPath, new Font(fontPath, Font.BOLD, 12));
                        } else {
                            System.err.println("Font file not found: " + fontPath + " - Falling back to default.");
                            cache.put(fontPath, new Font("Arial Rounded MT Bold", Font.BOLD, 12));
                        }
                    } else {
                        // Load the font and register it with the local Graphics Environment
                        Font customFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
                        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
                        cache.put(fontPath, customFont);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load custom font from: " + fontPath + " - Using default.");
                    e.printStackTrace();
                    cache.put(fontPath, new Font("Arial Rounded MT Bold", Font.BOLD, 12)); // Fallback
                }
            }
            // Return the cached font dynamically resized
            return cache.get(fontPath).deriveFont(Font.BOLD, size);
        }
    }

    static class EmojiLoader {
        private static final Map<String, BufferedImage> cache = new HashMap<>();

        public static BufferedImage getEmoji(String hex) {
            if (cache.containsKey(hex))
                return cache.get(hex);
            try {
                // Fetch the Twemoji graphic from jsdelivr CDN using the emoji's hex sequence
                URL url = new URI("https://cdn.jsdelivr.net/gh/jdecked/twemoji@15.1.0/assets/72x72/" + hex + ".png")
                        .toURL();
                BufferedImage img = ImageIO.read(url);
                cache.put(hex, img);
                return img;
            } catch (Exception e) {
                System.err.println("Could not load emoji: " + hex);
                cache.put(hex, null);
                return null;
            }
        }
    }

    private static String getTwemojiHex(String emojiString) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < emojiString.length(); i = emojiString.offsetByCodePoints(i, 1)) {
            if (hex.length() > 0)
                hex.append("-");
            int cp = emojiString.codePointAt(i);
            hex.append(Integer.toHexString(cp));
        }
        String result = hex.toString();
        // Twemoji usually strips the variation selector for standard emojis
        if (result.contains("-fe0f")) {
            result = result.replace("-fe0f", "");
        }
        return result;
    }

    static class TextRun {
        StringBuilder text = new StringBuilder();
        int style = Font.PLAIN;
        Color color = new Color(255, 255, 255);
        String gradType = "none";
        Color c1 = Color.WHITE;
        Color c2 = Color.WHITE;
        Color outlineColor = new Color(30, 81, 117); // Darker blueish border for thicker walls
        float outlineWidth = 7.0f; // Thick walls default (increased from 3.0)
        float fontSize = -1f; // -1 means use default base size
        String fontPath = null; // null means use global base font

        boolean isEmoji = false;
        BufferedImage emojiImage = null;

        boolean isLocalImage = false;
        BufferedImage localImage = null;

        boolean hasShine = true;
        Color shineColor = new Color(255, 255, 255, 127);

        TextRun copy() {
            TextRun copy = new TextRun();
            copy.style = this.style;
            copy.color = this.color;
            copy.gradType = this.gradType;
            copy.c1 = this.c1;
            copy.c2 = this.c2;
            copy.outlineColor = this.outlineColor;
            copy.outlineWidth = this.outlineWidth;
            copy.fontSize = this.fontSize;
            copy.fontPath = this.fontPath;
            copy.hasShine = this.hasShine;
            copy.shineColor = this.shineColor;
            return copy;
        }
    }

    static class LineData {
        List<TextRun> runs = new ArrayList<>();
        int width = 0;
        int height = 0;
        int ascent = 0;
        boolean hasBlob = false;
        int startX = 0;
        int startY = 0;
    }

    /**
     * Generates a composite image with a background, rich text, and a user profile
     * picture.
     *
     * @param backgroundPath The file path to the background image.
     * @param pfpUrl         The URL of the user's profile picture.
     * @param framePath      The path to the frame image to lay over the PFP.
     * @param fontPath       The path to a custom .ttf or .otf font file.
     * @param mainText       The text to display. Supports markdown (**bold**,
     *                       *italic*) and custom tags
     *                       like {c:#FF0000}, {g:letter:#FF0000:#00FF00},
     *                       {g:word:...}, {g:line:...}, and {/}
     * @param outputPath     The path where the resulting image should be saved.
     * @param pfpMarginRight Margin from the right edge for the PFP.
     * @param pfpMarginTop   Margin from the top edge for the PFP.
     * @param pfpSize        The width and height of the PFP.
     * @return true if generation was successful, false otherwise.
     */
    public static boolean generateMatchmakingImage(String backgroundPath, String pfpUrl, String framePath,
            String fontPath, String mainText,
            String outputPath, int pfpMarginRight, int pfpMarginTop, int pfpSize) {

        try {
            // 1. Load the background image
            File bgFile = new File(backgroundPath);
            if (!bgFile.exists()) {
                System.err.println("Background image not found at: " + backgroundPath);
                return false;
            }
            BufferedImage backgroundImage = ImageIO.read(bgFile);

            Graphics2D g2d = backgroundImage.createGraphics();

            // Set rendering hints for smooth graphics and text (Anti-aliasing)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 2. Fetch and draw the user's profile picture
            try {
                URL url = new URL(pfpUrl);
                BufferedImage pfpImage = ImageIO.read(url);

                if (pfpImage != null) {
                    // Calculate Top-Right position relative to background bounds
                    int pfpX = backgroundImage.getWidth() - pfpSize - pfpMarginRight;
                    int pfpY = pfpMarginTop;

                    // Crop PFP to a perfect square from the center to prevent squishing
                    int minDim = Math.min(pfpImage.getWidth(), pfpImage.getHeight());
                    int cropX = (pfpImage.getWidth() - minDim) / 2;
                    int cropY = (pfpImage.getHeight() - minDim) / 2;
                    BufferedImage croppedPfp = pfpImage.getSubimage(cropX, cropY, minDim, minDim);

                    // Draw the perfectly cropped PFP
                    g2d.drawImage(croppedPfp, pfpX, pfpY, pfpSize, pfpSize, null);

                    // Draw the custom frame image over the PFP
                    if (framePath != null && !framePath.isEmpty()) {
                        try {
                            File frameFile = new File(framePath);
                            if (frameFile.exists()) {
                                BufferedImage frameImage = ImageIO.read(frameFile);
                                
                                // Default config (matching the old 116% scale logic)
                                double configScale = 1.16;
                                double configOffsetX = 0.0;
                                double configOffsetY = 0.0;

                                // Try reading from frames_config.json
                                File configFile = new File("assets/frames_config.json");
                                if (configFile.exists()) {
                                    try (FileReader reader = new FileReader(configFile)) {
                                        java.lang.reflect.Type type = new TypeToken<Map<String, Map<String, Double>>>(){}.getType();
                                        Map<String, Map<String, Double>> configs = new Gson().fromJson(reader, type);
                                        if (configs != null && configs.containsKey(frameFile.getName())) {
                                            Map<String, Double> config = configs.get(frameFile.getName());
                                            if (config.containsKey("scale")) configScale = config.get("scale");
                                            if (config.containsKey("offsetX")) configOffsetX = config.get("offsetX");
                                            if (config.containsKey("offsetY")) configOffsetY = config.get("offsetY");
                                        }
                                    } catch (Exception ex) {
                                        System.err.println("Failed to read frame configs: " + ex.getMessage());
                                    }
                                }

                                // Use configScaleX as the master uniform scale, and mathematically derive the height
                                // to perfectly preserve the frame's native aspect ratio without warping!
                                int drawW = (int) (pfpSize * configScale);
                                int drawH = (int) (drawW / ((double) frameImage.getWidth() / frameImage.getHeight()));
                                
                                // Center the properly scaled frame over the PFP, then apply the custom X/Y translation offset
                                int drawX = pfpX + (pfpSize - drawW) / 2 + (int) (pfpSize * configOffsetX);
                                int drawY = pfpY + (pfpSize - drawH) / 2 + (int) (pfpSize * configOffsetY);

                                g2d.drawImage(frameImage, drawX, drawY, drawW, drawH, null);
                            } else {
                                System.err.println("Frame image not found at: " + framePath);
                            }
                        } catch (IOException ex) {
                            System.err.println("Failed to load frame image: " + framePath);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to load profile picture from URL: " + pfpUrl);
            }

            // 3. Draw the rich text
            if (mainText != null && !mainText.isEmpty()) {
                Font baseFont = FontLoader.getFont(fontPath, 40f); // Dynamically loads or fetches cached font

                // Default starting position for text
                int textX = 80; // Moved a tad right to match template margins
                int textY = 140; // Moved down a bit from the top edge

                // Calculate PFP collision bounds for dynamic wrapping
                int pfpBottom = pfpMarginTop + pfpSize + 30; // buffer below PFP
                int pfpLeft = backgroundImage.getWidth() - pfpSize - pfpMarginRight - 30; // buffer left of PFP

                List<LineData> parsedLines = layoutText(g2d, mainText, baseFont, textX, textY, backgroundImage.getWidth(), pfpBottom, pfpLeft);
                List<Rectangle> blobBounds = new ArrayList<>();

                for (LineData ld : parsedLines) {
                    if (ld.hasBlob) {
                        int paddingX = 40;
                        int paddingY = 25;
                        blobBounds.add(new Rectangle(ld.startX - paddingX, ld.startY - ld.ascent - paddingY,
                                ld.width + paddingX * 2, ld.height + paddingY * 2));
                    }
                }

                if (!blobBounds.isEmpty()) {
                    Color avgColor = getAverageColor(backgroundImage);
                    Color blobOverlay = new Color(avgColor.getRed(), avgColor.getGreen(), avgColor.getBlue(), 127);

                    int w = backgroundImage.getWidth();
                    int h = backgroundImage.getHeight();
                    int scale = 4; // Downscale factor for insanely fast blurring
                    int mw = Math.max(1, w / scale);
                    int mh = Math.max(1, h / scale);

                    // 1. Draw crisp overlapping pill shapes on a tiny mask
                    BufferedImage downscaledMask = new BufferedImage(mw, mh, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D dG = downscaledMask.createGraphics();
                    dG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    dG.setColor(Color.WHITE);
                    for (Rectangle r : blobBounds) {
                        int rx = r.x / scale;
                        int ry = r.y / scale;
                        int rw = r.width / scale;
                        int rh = r.height / scale;
                        int arc = 60 / scale;
                        dG.fillRoundRect(rx, ry, rw, rh, arc, arc);
                    }
                    dG.dispose();

                    // 2. Perform a fast, small radius blur on the tiny mask to soften intersections
                    float[] matrix = new float[9];
                    Arrays.fill(matrix, 1.0f / 9.0f);
                    ConvolveOp blurOp = new ConvolveOp(new Kernel(3, 3, matrix), ConvolveOp.EDGE_NO_OP, null);
                    BufferedImage tempMask = new BufferedImage(mw, mh, BufferedImage.TYPE_INT_ARGB);
                    blurOp.filter(downscaledMask, tempMask);
                    blurOp.filter(tempMask, downscaledMask); // Double pass for smoother blend

                    // 3. Upscale the mask natively using Bilinear interpolation. This turns the
                    // small blur into a giant, perfectly smooth metaball effect!
                    BufferedImage upscaledMask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D uG = upscaledMask.createGraphics();
                    uG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    uG.drawImage(downscaledMask, 0, 0, w, h, null);
                    uG.dispose();

                    // 4. Slightly blur the background
                    BufferedImage blurredBg = createBlurredBackground(backgroundImage);

                    // 5. Composite the blur and color onto the background using the gooey mask
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int maskAlpha = (upscaledMask.getRGB(x, y) >> 24) & 0xFF;
                            if (maskAlpha > 120) { // Thresholding to snap the blurred edges into a solid "blob"
                                int bgPix = blurredBg.getRGB(x, y);
                                int finalPix = blendColor(bgPix, blobOverlay);

                                // Soften the absolute outer pixel of the blob for anti-aliasing
                                float weight = Math.min(1.0f, (maskAlpha - 110) / 20.0f);
                                if (weight >= 1.0f) {
                                    backgroundImage.setRGB(x, y, finalPix);
                                } else {
                                    int origPix = backgroundImage.getRGB(x, y);
                                    backgroundImage.setRGB(x, y, blendWeighted(origPix, finalPix, weight));
                                }
                            }
                        }
                    }
                }

                for (LineData ld : parsedLines) {
                    drawParsedLine(g2d, ld, baseFont);
                }
            }

            // 4. Clean up graphics resources
            g2d.dispose();

            // 5. Save the final image
            File outputFile = new File(outputPath);
            ImageIO.write(backgroundImage, "png", outputFile);

            System.out.println("Image successfully generated at: " + outputPath);
            return true;

        } catch (Exception e) {
            System.err.println("An error occurred during image generation.");
            e.printStackTrace();
            return false;
        }
    }

    private static List<LineData> layoutText(Graphics2D g2d, String mainText, Font baseFont, int startX, int startY, int bgWidth, int pfpBottom, int pfpLeft) {
        List<LineData> finalLines = new ArrayList<>();
        String[] paragraphs = mainText.split("\n"); 
        int currentY = startY;
        boolean previousWasBlob = false;

        for (String paragraph : paragraphs) {
            boolean hasBlob = false;
            if (paragraph.contains("{blob}")) {
                hasBlob = true;
                paragraph = paragraph.replace("{blob}", ""); // Consume the tag quietly
            }

            if (hasBlob && previousWasBlob) {
                currentY -= 25; // Pull consecutive blob paragraphs (like Name/Handle) closer together
            }

            List<TextRun> parsedRuns = parseRichText(paragraph);
            
            // Handle completely empty lines gracefully
            if (parsedRuns.isEmpty()) {
                LineData blankLine = new LineData();
                blankLine.hasBlob = hasBlob;
                FontMetrics fm = g2d.getFontMetrics(baseFont);
                blankLine.height = fm.getHeight();
                blankLine.ascent = fm.getAscent();
                blankLine.startX = startX;
                blankLine.startY = currentY;
                finalLines.add(blankLine);
                
                currentY += blankLine.height - 4;
                previousWasBlob = hasBlob;
                continue;
            }

            LineData currentLine = new LineData();
            currentLine.hasBlob = hasBlob;
            int currentLineWidth = 0;
            int maxLineHeight = 0;
            int maxLineAscent = 0;

            for (TextRun run : parsedRuns) {
                Font runBase = (run.fontPath != null && !run.fontPath.isEmpty()) ? FontLoader.getFont(run.fontPath, 36f) : baseFont;
                Font runFont = run.fontSize > 0 ? runBase.deriveFont(run.style).deriveFont(run.fontSize) : runBase.deriveFont(run.style);
                FontMetrics fm = g2d.getFontMetrics(runFont);

                if (run.isEmoji) {
                    int runWidth = fm.getHeight() + 2;
                    int maxWidth = (currentY < pfpBottom) ? (pfpLeft - startX) : (bgWidth - startX - 80);
                    
                    if (currentLineWidth + runWidth > maxWidth && currentLineWidth > 0) {
                        currentLine.width = currentLineWidth;
                        if (maxLineHeight == 0) { maxLineHeight = fm.getHeight(); maxLineAscent = fm.getAscent(); }
                        currentLine.height = maxLineHeight;
                        currentLine.ascent = maxLineAscent;
                        currentLine.startX = startX;
                        currentLine.startY = currentY;
                        finalLines.add(currentLine);
                        
                        currentY += currentLine.height - 4;
                        currentLine = new LineData();
                        currentLine.hasBlob = hasBlob;
                        currentLineWidth = 0;
                        maxLineHeight = 0;
                        maxLineAscent = 0;
                        maxWidth = (currentY < pfpBottom) ? (pfpLeft - startX) : (bgWidth - startX - 80);
                    }
                    
                    currentLine.runs.add(run);
                    currentLineWidth += runWidth;
                    maxLineHeight = Math.max(maxLineHeight, fm.getHeight());
                    maxLineAscent = Math.max(maxLineAscent, fm.getAscent());
                } 
                else if (run.isLocalImage && run.localImage != null) {
                    int runWidth = (int) ((double) run.localImage.getWidth() / run.localImage.getHeight() * fm.getHeight()) + 5;
                    int maxWidth = (currentY < pfpBottom) ? (pfpLeft - startX) : (bgWidth - startX - 80);
                    
                    if (currentLineWidth + runWidth > maxWidth && currentLineWidth > 0) {
                        currentLine.width = currentLineWidth;
                        if (maxLineHeight == 0) { maxLineHeight = fm.getHeight(); maxLineAscent = fm.getAscent(); }
                        currentLine.height = maxLineHeight;
                        currentLine.ascent = maxLineAscent;
                        currentLine.startX = startX;
                        currentLine.startY = currentY;
                        finalLines.add(currentLine);
                        
                        currentY += currentLine.height - 4;
                        currentLine = new LineData();
                        currentLine.hasBlob = hasBlob;
                        currentLineWidth = 0;
                        maxLineHeight = 0;
                        maxLineAscent = 0;
                    }
                    
                    currentLine.runs.add(run);
                    currentLineWidth += runWidth;
                    maxLineHeight = Math.max(maxLineHeight, fm.getHeight());
                    maxLineAscent = Math.max(maxLineAscent, fm.getAscent());
                } 
                else {
                    // Standard Text: Split by spaces but explicitly keep the space tokens to preserve spacing!
                    String[] words = run.text.toString().split("(?<=\\s)|(?=\\s)");
                    TextRun currentWordRun = run.copy();
                    currentWordRun.text = new StringBuilder();
                    
                    for (String word : words) {
                        if (word.isEmpty()) continue;
                        int wordWidth = fm.stringWidth(word);
                        int maxWidth = (currentY < pfpBottom) ? (pfpLeft - startX) : (bgWidth - startX - 80);
                        
                        // Prevent leading spaces from aggressively pushing a wrapped line rightward
                        if (currentLineWidth == 0 && word.trim().isEmpty()) continue;
                        
                        // If it overflows the allowed bounds, snap it to the next line below!
                        if (currentLineWidth + wordWidth > maxWidth && currentLineWidth > 0) {
                            if (currentWordRun.text.length() > 0) {
                                currentLine.runs.add(currentWordRun);
                            }
                            currentLine.width = currentLineWidth;
                            if (maxLineHeight == 0) { maxLineHeight = fm.getHeight(); maxLineAscent = fm.getAscent(); }
                            currentLine.height = maxLineHeight;
                            currentLine.ascent = maxLineAscent;
                            currentLine.startX = startX;
                            currentLine.startY = currentY;
                            finalLines.add(currentLine);
                            
                            currentY += currentLine.height - 4;
                            currentLine = new LineData();
                            currentLine.hasBlob = hasBlob;
                            currentLineWidth = 0;
                            maxLineHeight = 0;
                            maxLineAscent = 0;
                            
                            currentWordRun = run.copy();
                            currentWordRun.text = new StringBuilder();
                            
                            if (word.trim().isEmpty()) continue;
                        }
                        
                        currentWordRun.text.append(word);
                        currentLineWidth += wordWidth;
                        maxLineHeight = Math.max(maxLineHeight, fm.getHeight());
                        maxLineAscent = Math.max(maxLineAscent, fm.getAscent());
                    }
                    if (currentWordRun.text.length() > 0) {
                        currentLine.runs.add(currentWordRun);
                    }
                }
            }
            
            if (!currentLine.runs.isEmpty() || currentLine.width > 0) {
                currentLine.width = currentLineWidth;
                if (maxLineHeight == 0) { 
                    FontMetrics fm = g2d.getFontMetrics(baseFont);
                    maxLineHeight = fm.getHeight(); 
                    maxLineAscent = fm.getAscent(); 
                }
                currentLine.height = maxLineHeight;
                currentLine.ascent = maxLineAscent;
                currentLine.startX = startX;
                currentLine.startY = currentY;
                finalLines.add(currentLine);
                currentY += currentLine.height - 4;
            }
            
            previousWasBlob = hasBlob;
        }
        return finalLines;
    }

    private static List<TextRun> parseRichText(String line) {
        List<TextRun> runs = new ArrayList<>();
        // 1. Pre-process real emojis
        line = EmojiParser.parseFromUnicode(line,
                candidate -> "{e:" + getTwemojiHex(candidate.getEmoji().getUnicode()) + "}");

        TextRun currentRun = new TextRun();

        // Inline parser for tags
        for (int i = 0; i < line.length(); i++) {
            if (line.startsWith("**", i)) {
                if (currentRun.text.length() > 0) {
                    runs.add(currentRun);
                    currentRun = currentRun.copy();
                    currentRun.text = new StringBuilder();
                }
                currentRun.style ^= Font.BOLD;
                i++; // skip second asterisk
            } else if (line.startsWith("*", i)) {
                if (currentRun.text.length() > 0) {
                    runs.add(currentRun);
                    currentRun = currentRun.copy();
                    currentRun.text = new StringBuilder();
                }
                currentRun.style ^= Font.ITALIC;
            } else if (line.startsWith("{", i)) {
                int end = line.indexOf('}', i);
                if (end != -1) {
                    String tag = line.substring(i + 1, end);
                    if (currentRun.text.length() > 0) {
                        runs.add(currentRun);
                        currentRun = currentRun.copy();
                        currentRun.text = new StringBuilder();
                    }

                    try {
                        if (tag.equals("/")) {
                            currentRun.color = new Color(235, 242, 250);
                            currentRun.gradType = "none";
                            currentRun.fontSize = -1f; // Reset font size to default
                            currentRun.fontPath = null; // Reset font path to default
                            currentRun.hasShine = true; // Reset shine
                            currentRun.shineColor = new Color(255, 255, 255, 220); // Reset shine color
                        } else if (tag.startsWith("c:")) {
                            Color parsedColor = Color.decode(tag.substring(2));
                            // If user explicitly asks for pure white, intercept it with icy white so the
                            // shine still works
                            if (parsedColor.equals(Color.WHITE)) {
                                currentRun.color = new Color(235, 242, 250);
                            } else {
                                currentRun.color = parsedColor;
                            }
                            currentRun.gradType = "none";
                        } else if (tag.equals("shine")) {
                            currentRun.hasShine = true;
                        } else if (tag.equals("noshine")) {
                            currentRun.hasShine = false;
                        } else if (tag.startsWith("sc:")) {
                            Color parsedColor = Color.decode(tag.substring(3));
                            currentRun.shineColor = new Color(parsedColor.getRed(), parsedColor.getGreen(),
                                    parsedColor.getBlue(), 220);
                        } else if (tag.startsWith("e:")) {
                            String hex = tag.substring(2);
                            TextRun emojiRun = currentRun.copy();
                            emojiRun.isEmoji = true;
                            emojiRun.emojiImage = EmojiLoader.getEmoji(hex);
                            runs.add(emojiRun);
                        } else if (tag.startsWith("img:")) {
                            String path = "assets/customojis/" + tag.substring(4);
                            TextRun imgRun = currentRun.copy();
                            imgRun.isLocalImage = true;
                            try {
                                imgRun.localImage = ImageIO.read(new File(path));
                            } catch (Exception ex) {
                                System.err.println("Could not load local image: " + path);
                            }
                            runs.add(imgRun);
                        } else if (tag.startsWith("g:")) {
                            String[] parts = tag.split(":");
                            if (parts.length == 4) {
                                currentRun.gradType = parts[1].toLowerCase();
                                currentRun.c1 = Color.decode(parts[2]);
                                currentRun.c2 = Color.decode(parts[3]);
                            }
                        } else if (tag.startsWith("o:")) {
                            String[] parts = tag.split(":");
                            if (parts.length == 3) {
                                currentRun.outlineColor = Color.decode(parts[1]);
                                currentRun.outlineWidth = Float.parseFloat(parts[2]);
                            }
                        } else if (tag.equals("o/")) {
                            currentRun.outlineWidth = 0;
                        } else if (tag.startsWith("s:")) {
                            // Dynamic font sizing tag {s:size}
                            currentRun.fontSize = Float.parseFloat(tag.substring(2));
                        } else if (tag.startsWith("f:")) {
                            // Dynamic font changing tag {f:fontPath}
                            currentRun.fontPath = tag.substring(2);
                        }
                    } catch (Exception ignored) {
                        // If color decoding fails, safely ignore the tag formatting
                    }
                    i = end;
                } else {
                    currentRun.text.append(line.charAt(i));
                }
            } else {
                currentRun.text.append(line.charAt(i));
            }
        }
        if (currentRun.text.length() > 0) {
            runs.add(currentRun);
        }
        
        return runs;
    }

    private static void drawParsedLine(Graphics2D g2d, LineData ld, Font baseFont) {
        int currentX = ld.startX;
        int startY = ld.startY;
        int totalLineWidth = ld.width;

        for (TextRun run : ld.runs) {
            // Determine the base font for this specific run
            Font runBase = (run.fontPath != null && !run.fontPath.isEmpty()) ? FontLoader.getFont(run.fontPath, 36f)
                    : baseFont;

            Font runFont = run.fontSize > 0 ? runBase.deriveFont(run.style).deriveFont(run.fontSize)
                    : runBase.deriveFont(run.style);
            g2d.setFont(runFont);
            FontMetrics fm = g2d.getFontMetrics();

            if (run.isEmoji) {
                if (run.emojiImage != null) {
                    int size = fm.getHeight(); // Scale emoji size to perfectly match the current font height
                    int yOffset = startY - fm.getAscent();

                    // Draw the soft drop shadow
                    drawSoftDropShadow(g2d, run.emojiImage, currentX, yOffset, size, size);

                    // Draw the actual colored emoji on top
                    g2d.drawImage(run.emojiImage, currentX, yOffset, size, size, null);
                }
                currentX += fm.getHeight() + 2;
                continue;
            }

            if (run.isLocalImage) {
                if (run.localImage != null) {
                    int targetHeight = fm.getHeight(); // Match text height
                    // Calculate target width to preserve the image's original aspect ratio
                    int targetWidth = (int) ((double) run.localImage.getWidth() / run.localImage.getHeight()
                            * targetHeight);
                    int yOffset = startY - fm.getAscent();

                    // Draw the soft drop shadow
                    drawSoftDropShadow(g2d, run.localImage, currentX, yOffset, targetWidth, targetHeight);

                    // Draw the actual colored image on top
                    g2d.drawImage(run.localImage, currentX, yOffset, targetWidth, targetHeight, null);

                    currentX += targetWidth + 5; // Add 5px padding after the image
                }
                continue;
            }

            String runString = run.text.toString();

            if (run.gradType.equals("line")) {
                Paint p = new GradientPaint(ld.startX, startY - fm.getAscent(), run.c1, ld.startX + totalLineWidth,
                        startY, run.c2);
                drawStrokedString(g2d, runString, currentX, startY, run.outlineColor, run.outlineWidth, p, run.hasShine,
                        run.shineColor);
                currentX += fm.stringWidth(runString);
            } else if (run.gradType.equals("word")) {
                String[] words = runString.split("(?<=\\s)|(?=\\s)"); // Keep spaces as tokens so spacing isn't lost
                for (String word : words) {
                    int wWidth = fm.stringWidth(word);
                    if (!word.trim().isEmpty()) {
                        Paint p = new GradientPaint(currentX, startY - fm.getAscent(), run.c1, currentX + wWidth,
                                startY, run.c2);
                        drawStrokedString(g2d, word, currentX, startY, run.outlineColor, run.outlineWidth, p,
                                run.hasShine, run.shineColor);
                    }
                    currentX += wWidth;
                }
            } else if (run.gradType.equals("letter")) {
                for (char c : runString.toCharArray()) {
                    String s = String.valueOf(c);
                    int cWidth = fm.stringWidth(s);
                    if (c != ' ') {
                        Paint p = new GradientPaint(currentX, startY - fm.getAscent(), run.c1, currentX + cWidth,
                                startY, run.c2);
                        drawStrokedString(g2d, s, currentX, startY, run.outlineColor, run.outlineWidth, p, run.hasShine,
                                run.shineColor);
                    }
                    currentX += cWidth;
                }
            } else {
                if (!runString.trim().isEmpty()) {
                    drawStrokedString(g2d, runString, currentX, startY, run.outlineColor, run.outlineWidth, run.color,
                            run.hasShine, run.shineColor);
                }
                currentX += fm.stringWidth(runString);
            }
        }
    }

    private static Color getAverageColor(BufferedImage img) {
        long r = 0, g = 0, b = 0;
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);
        for (int argb : pixels) {
            r += (argb >> 16) & 0xFF;
            g += (argb >> 8) & 0xFF;
            b += argb & 0xFF;
        }
        int count = pixels.length;
        if (count == 0)
            return Color.WHITE;
        return new Color((int) (r / count), (int) (g / count), (int) (b / count));
    }

    private static BufferedImage createBlurredBackground(BufferedImage src) {
        int radius = 2; // Subtle blur length
        int size = radius * 2 + 1;
        float weight = 1.0f / (size * size);
        float[] data = new float[size * size];
        Arrays.fill(data, weight);

        Kernel kernel = new Kernel(size, size, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);

        // Ensure image type is cleanly supported by ConvolveOp
        BufferedImage convertedSrc = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = convertedSrc.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        op.filter(convertedSrc, dest);
        return dest;
    }

    private static int blendColor(int basePixel, Color overlay) {
        int r = (basePixel >> 16) & 0xFF;
        int g = (basePixel >> 8) & 0xFF;
        int b = basePixel & 0xFF;

        float a2 = overlay.getAlpha() / 255.0f;
        float a1 = 1.0f - a2;

        int rOut = (int) (r * a1 + overlay.getRed() * a2);
        int gOut = (int) (g * a1 + overlay.getGreen() * a2);
        int bOut = (int) (b * a1 + overlay.getBlue() * a2);

        return (0xFF << 24) | (rOut << 16) | (gOut << 8) | bOut;
    }

    private static int blendWeighted(int orig, int newPix, float weight) {
        int r1 = (orig >> 16) & 0xFF;
        int g1 = (orig >> 8) & 0xFF;
        int b1 = orig & 0xFF;
        int r2 = (newPix >> 16) & 0xFF;
        int g2 = (newPix >> 8) & 0xFF;
        int b2 = newPix & 0xFF;

        float w1 = 1.0f - weight;
        int rOut = (int) (r1 * w1 + r2 * weight);
        int gOut = (int) (g1 * w1 + g2 * weight);
        int bOut = (int) (b1 * w1 + b2 * weight);

        return (0xFF << 24) | (rOut << 16) | (gOut << 8) | bOut;
    }

    private static void drawSoftDropShadow(Graphics2D g2d, BufferedImage originalImage, int targetX, int targetY,
            int targetWidth, int targetHeight) {
        // 1. Create a silhouette of the image for the shadow
        BufferedImage shadowImg = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = shadowImg.createGraphics();

        // Draw the original image to get the alpha mask
        sg.drawImage(originalImage, 0, 0, null);

        // Use SrcIn composite to fill only the non-transparent pixels with black
        sg.setComposite(AlphaComposite.SrcIn);
        sg.setColor(new Color(0, 0, 0, 12)); // Very low opacity (about 5%) for stacking a smooth blur
        sg.fillRect(0, 0, shadowImg.getWidth(), shadowImg.getHeight());
        sg.dispose();

        // 2. The multi-pass shadow offsets to create a soft all-around glow +
        // directional drop
        int[][] offsets = {
                // Floating all around (soft base)
                { -2, -2 }, { 0, -2 }, { 2, -2 },
                { -2, 0 }, { 0, 0 }, { 2, 0 },
                { -2, 2 }, { 0, 2 }, { 2, 2 },
                { -1, -1 }, { 0, -1 }, { 1, -1 },
                { -1, 0 }, { 1, 0 },
                { -1, 1 }, { 0, 1 }, { 1, 1 },

                // Core direction (bottom right stretch)
                { 2, 3 }, { 3, 2 },
                { 3, 3 }, { 3, 4 }, { 4, 3 },
                { 4, 4 }, { 4, 5 }, { 5, 4 },
                { 5, 5 }
        };

        // 3. Draw the scaled silhouette at each offset to build up the soft shadow
        for (int[] offset : offsets) {
            g2d.drawImage(shadowImg, targetX + offset[0], targetY + offset[1], targetWidth, targetHeight, null);
        }
    }

    private static void drawStrokedString(Graphics2D g2d, String text, int x, int y, Color outlineColor,
            float outlineWidth, Paint fillPaint, boolean addShine, Color shineColor) {
        if (text == null || text.trim().isEmpty())
            return;

        FontRenderContext frc = g2d.getFontRenderContext();
        TextLayout tl = new TextLayout(text, g2d.getFont(), frc);
        Shape shape = tl.getOutline(AffineTransform.getTranslateInstance(x, y));

        if (outlineWidth > 0 && outlineColor != null) {
            Paint originalPaint = g2d.getPaint();
            Stroke originalStroke = g2d.getStroke();

            g2d.setColor(outlineColor);
            g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(shape);

            g2d.setPaint(originalPaint);
            g2d.setStroke(originalStroke);
        }

        if (fillPaint != null) {
            Paint originalPaint = g2d.getPaint();
            g2d.setPaint(fillPaint);
            g2d.fill(shape);

            // Programmatic Glass Reflection / Shine overlay!
            if (addShine) {
                Shape originalClip = g2d.getClip();
                g2d.clip(shape); // Ensures the shine only draws INSIDE the letters

                FontMetrics fm = g2d.getFontMetrics();
                int topY = y - fm.getAscent();
                int height = fm.getAscent();

                // Draw a gradient that is the requested shine color at the top, fading to
                // transparent midway down the letter
                GradientPaint shinePaint = new GradientPaint(
                        0, topY, shineColor,
                        0, topY + (height * 0.7f),
                        new Color(shineColor.getRed(), shineColor.getGreen(), shineColor.getBlue(), 0));

                g2d.setPaint(shinePaint);
                g2d.fill(shape.getBounds()); // Fill the bounded area, clipping handles the rest

                g2d.setClip(originalClip); // Restore clip
            }

            g2d.setPaint(originalPaint);
        }
    }

    /**
     * A simplified wrapper that generates the image and returns a File object,
     * using the user's ID to prevent filename collisions.
     * * @param backgroundPath The path to the background image template.
     * * @param pfpUrl The URL of the user's avatar.
     * 
     * @param framePath The path to the custom frame image.
     * @param fontPath  The path to the custom .ttf font file.
     * @param mainText  The text to render onto the image.
     * @param userId    The Discord user ID (used for the temporary file name).
     * @return The generated File, ready to be sent via JDA, or null if it failed.
     */
    public static File generateForUser(String backgroundPath, String pfpUrl, String framePath, String fontPath,
            String mainText,
            String userId) {
        // Create a unique filename for this specific user's generated image
        String outputPath = "matchmaking_" + userId + ".png";

        // Call the main generator with reasonable default coordinates/sizes based on
        // the reference
        int defaultMarginRight = 134;
        int defaultMarginTop = 50;
        int defaultPfpSize = 350;

        boolean success = generateMatchmakingImage(backgroundPath, pfpUrl, framePath, fontPath, mainText, outputPath,
                defaultMarginRight, defaultMarginTop, defaultPfpSize);

        if (success) {
            return new File(outputPath);
        }
        return null;
    }

    /**
     * Decodes a 5-7 character design code (e.g. "DEF-DEF") into actual file paths.
     * @param designCode The code provided by the user.
     * @return An array containing [Background Path, Frame Path].
     */
    public static String[] decodeDesignCode(String designCode) {
        String bgFile = "default.png";
        String frameFile = "default.png";
        
        if (designCode != null && designCode.contains("-")) {
            String[] parts = designCode.trim().toUpperCase().split("-");
            if (parts.length >= 2) {
                bgFile = resolveCodeToFilename(parts[0], "backgrounds");
                frameFile = resolveCodeToFilename(parts[1], "frames");
            }
        }
        return new String[]{ "assets/backgrounds/" + bgFile, "assets/frames/" + frameFile };
    }

    private static String resolveCodeToFilename(String code, String category) {
        // 1. Check design_codes.json config map
        File configFile = new File("assets/design_codes.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                java.lang.reflect.Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
                Map<String, Map<String, String>> configs = new Gson().fromJson(reader, type);
                if (configs != null && configs.containsKey(category)) {
                    for (Map.Entry<String, String> entry : configs.get(category).entrySet()) {
                        if (entry.getValue().toUpperCase().equals(code)) {
                            return entry.getKey();
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to read design codes: " + ex.getMessage());
            }
        }
        
        // 2. Fallback search by auto-hashing available files if not in config
        File dir = new File("assets/" + category + "/");
        if (dir.exists() && dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                String nameOnly = file.getName();
                int dotIndex = nameOnly.lastIndexOf('.');
                if(dotIndex > 0) nameOnly = nameOnly.substring(0, dotIndex);
                
                String clean = nameOnly.replaceAll("[^A-Za-z0-9]", "");
                String fallbackCode = clean.length() >= 3 ? clean.substring(0, 3).toUpperCase() : clean.toUpperCase();
                
                if (fallbackCode.equals(code)) {
                    return file.getName();
                }
            }
        }
        return "default.png";
    }
}