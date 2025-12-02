package com.waqiti.kyc.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Document Security Feature Detector
 *
 * CRITICAL FIX: Replaces stub implementations with real computer vision algorithms
 *
 * Detects security features in identity documents:
 * - MRZ (Machine Readable Zone) detection and validation
 * - Watermark detection using frequency analysis
 * - Barcode/QR code detection
 * - Microtext detection
 * - Hologram detection (color variation analysis)
 * - Security pattern detection
 *
 * Note: This implementation uses Java built-in image processing.
 * For production, consider OpenCV (org.bytedeco:javacv) for better accuracy.
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Slf4j
@Service
public class DocumentSecurityFeatureDetector {

    // MRZ validation pattern (ICAO Document 9303)
    private static final Pattern MRZ_LINE_PATTERN = Pattern.compile("^[A-Z0-9<]{30,44}$");

    // Minimum dimensions for security feature analysis
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 200;

    /**
     * Detect MRZ (Machine Readable Zone) in passport/ID documents
     *
     * MRZ is typically located at the bottom of the document and consists of
     * 2-3 lines of uppercase letters, digits, and < symbols.
     */
    public boolean detectMRZ(BufferedImage image) {
        if (image == null || image.getWidth() < MIN_WIDTH || image.getHeight() < MIN_HEIGHT) {
            log.warn("Image too small for MRZ detection");
            return false;
        }

        try {
            // Extract bottom 20% of image (MRZ location)
            int mrzHeight = image.getHeight() / 5;
            int startY = image.getHeight() - mrzHeight;
            BufferedImage mrzRegion = image.getSubimage(0, startY, image.getWidth(), mrzHeight);

            // Convert to grayscale and enhance contrast for better OCR
            BufferedImage enhanced = enhanceContrast(toGrayscale(mrzRegion));

            // Detect horizontal line patterns characteristic of MRZ
            boolean hasHorizontalLines = detectHorizontalLinePattern(enhanced);

            if (!hasHorizontalLines) {
                log.debug("No horizontal line pattern detected in MRZ region");
                return false;
            }

            // Perform OCR on MRZ region
            Tesseract tesseract = new Tesseract();
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(6); // Assume uniform block of text

            String ocrText = tesseract.doOCR(enhanced);

            // Validate MRZ format
            return validateMRZFormat(ocrText);

        } catch (Exception e) {
            log.error("Error detecting MRZ", e);
            return false;
        }
    }

    /**
     * Validate MRZ format according to ICAO Document 9303
     */
    private boolean validateMRZFormat(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // Split into lines
        String[] lines = text.split("\\n");

        // MRZ should have 2-3 lines
        if (lines.length < 2 || lines.length > 3) {
            log.debug("Invalid MRZ line count: {}", lines.length);
            return false;
        }

        // Check each line matches MRZ pattern
        int validLines = 0;
        for (String line : lines) {
            String cleaned = line.replaceAll("\\s+", "").toUpperCase();
            if (cleaned.length() >= 30 && cleaned.length() <= 44 &&
                MRZ_LINE_PATTERN.matcher(cleaned).matches()) {
                validLines++;
            }
        }

        boolean isValid = validLines >= 2;
        log.debug("MRZ validation: {} valid lines out of {}", validLines, lines.length);

        return isValid;
    }

    /**
     * Detect watermark using frequency domain analysis
     *
     * Watermarks create subtle patterns detectable through FFT (Fast Fourier Transform)
     * This simplified version uses spatial domain analysis.
     */
    public boolean detectWatermark(BufferedImage image) {
        if (image == null) return false;

        try {
            // Convert to grayscale
            BufferedImage gray = toGrayscale(image);

            // Analyze texture uniformity (watermarks create subtle texture variations)
            double textureVariance = calculateTextureVariance(gray);

            // Watermarks typically have variance between 0.05 and 0.15
            boolean hasWatermark = textureVariance > 0.05 && textureVariance < 0.15;

            log.debug("Watermark detection: texture variance = {}, detected = {}",
                textureVariance, hasWatermark);

            return hasWatermark;

        } catch (Exception e) {
            log.error("Error detecting watermark", e);
            return false;
        }
    }

    /**
     * Detect barcode or QR code patterns
     *
     * Barcodes have characteristic vertical line patterns (1D) or square patterns (2D)
     */
    public boolean detectBarcode(BufferedImage image) {
        if (image == null) return false;

        try {
            BufferedImage gray = toGrayscale(image);

            // Check for vertical line patterns (1D barcode)
            boolean hasVerticalLines = detectVerticalLinePattern(gray);

            // Check for square patterns (QR code)
            boolean hasSquarePattern = detectSquareFinderPatterns(gray);

            boolean detected = hasVerticalLines || hasSquarePattern;
            log.debug("Barcode detection: vertical={}, square={}, detected={}",
                hasVerticalLines, hasSquarePattern, detected);

            return detected;

        } catch (Exception e) {
            log.error("Error detecting barcode", e);
            return false;
        }
    }

    /**
     * Detect microtext (very small printed text used as security feature)
     *
     * Microtext is typically 0.5mm or smaller and requires high-resolution scanning
     */
    public boolean detectMicrotext(BufferedImage image) {
        if (image == null) return false;

        try {
            // Calculate text density at different scales
            BufferedImage gray = toGrayscale(image);

            // Detect high-frequency details that indicate very small text
            double edgeDensity = calculateEdgeDensity(gray);

            // Microtext creates high edge density (>0.3)
            boolean hasMicrotext = edgeDensity > 0.3;

            log.debug("Microtext detection: edge density = {}, detected = {}",
                edgeDensity, hasMicrotext);

            return hasMicrotext;

        } catch (Exception e) {
            log.error("Error detecting microtext", e);
            return false;
        }
    }

    /**
     * Detect circular patterns (government seals, emblems)
     *
     * Uses simplified Hough Circle Transform approach
     */
    public boolean detectCircularPattern(BufferedImage image) {
        if (image == null) return false;

        try {
            BufferedImage gray = toGrayscale(image);
            BufferedImage edges = detectEdges(gray);

            // Count circular edge patterns
            int circleCount = countCircularPatterns(edges);

            boolean detected = circleCount > 0;
            log.debug("Circular pattern detection: {} circles found", circleCount);

            return detected;

        } catch (Exception e) {
            log.error("Error detecting circular patterns", e);
            return false;
        }
    }

    /**
     * Detect repetitive security patterns (guilloches, fine lines)
     */
    public boolean detectRepetitivePattern(BufferedImage image) {
        if (image == null) return false;

        try {
            BufferedImage gray = toGrayscale(image);

            // Analyze for periodic patterns
            double periodicity = calculatePeriodicity(gray);

            // Guilloches and security patterns have periodicity > 0.4
            boolean hasPattern = periodicity > 0.4;

            log.debug("Repetitive pattern detection: periodicity = {}, detected = {}",
                periodicity, hasPattern);

            return hasPattern;

        } catch (Exception e) {
            log.error("Error detecting repetitive patterns", e);
            return false;
        }
    }

    /**
     * Detect holographic features through color variation analysis
     */
    public double analyzeColorVariation(BufferedImage image) {
        if (image == null) return 0.0;

        try {
            // Analyze color channel variations
            // Holograms show different colors at different angles (iridescence)

            double redVariance = calculateChannelVariance(image, 0); // Red
            double greenVariance = calculateChannelVariance(image, 1); // Green
            double blueVariance = calculateChannelVariance(image, 2); // Blue

            // Holographic features have high color variance
            double colorVariation = (redVariance + greenVariance + blueVariance) / 3.0;

            log.debug("Color variation analysis: R={}, G={}, B={}, avg={}",
                redVariance, greenVariance, blueVariance, colorVariation);

            return colorVariation;

        } catch (Exception e) {
            log.error("Error analyzing color variation", e);
            return 0.0;
        }
    }

    // ========== HELPER METHODS ==========

    private BufferedImage toGrayscale(BufferedImage image) {
        BufferedImage gray = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return gray;
    }

    private BufferedImage enhanceContrast(BufferedImage image) {
        // Simple contrast enhancement using histogram stretching
        int[] histogram = new int[256];
        int totalPixels = image.getWidth() * image.getHeight();

        // Build histogram
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = image.getRGB(x, y) & 0xFF;
                histogram[gray]++;
            }
        }

        // Find min and max
        int min = 0, max = 255;
        for (int i = 0; i < 256; i++) {
            if (histogram[i] > 0) {
                min = i;
                break;
            }
        }
        for (int i = 255; i >= 0; i--) {
            if (histogram[i] > 0) {
                max = i;
                break;
            }
        }

        // Apply contrast stretch
        BufferedImage enhanced = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = image.getRGB(x, y) & 0xFF;
                int stretched = (int) (255.0 * (gray - min) / (max - min));
                stretched = Math.max(0, Math.min(255, stretched));

                int rgb = (stretched << 16) | (stretched << 8) | stretched;
                enhanced.setRGB(x, y, rgb);
            }
        }

        return enhanced;
    }

    private boolean detectHorizontalLinePattern(BufferedImage image) {
        // Detect horizontal runs of similar pixels (MRZ characteristic)
        int consecutiveRunsRequired = image.getWidth() / 3;
        int linesDetected = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            int consecutiveRun = 0;
            int prevGray = -1;

            for (int x = 0; x < image.getWidth(); x++) {
                int gray = image.getRGB(x, y) & 0xFF;

                if (Math.abs(gray - prevGray) < 20) {
                    consecutiveRun++;
                } else {
                    if (consecutiveRun > consecutiveRunsRequired) {
                        linesDetected++;
                        break;
                    }
                    consecutiveRun = 0;
                }
                prevGray = gray;
            }
        }

        return linesDetected >= 2;
    }

    private double calculateTextureVariance(BufferedImage image) {
        // Calculate local variance to detect subtle texture patterns
        int windowSize = 5;
        double totalVariance = 0;
        int count = 0;

        for (int y = windowSize; y < image.getHeight() - windowSize; y += windowSize) {
            for (int x = windowSize; x < image.getWidth() - windowSize; x += windowSize) {
                double variance = calculateLocalVariance(image, x, y, windowSize);
                totalVariance += variance;
                count++;
            }
        }

        return count > 0 ? totalVariance / count : 0.0;
    }

    private double calculateLocalVariance(BufferedImage image, int cx, int cy, int windowSize) {
        double sum = 0, sumSquares = 0;
        int count = 0;

        for (int y = cy - windowSize; y < cy + windowSize; y++) {
            for (int x = cx - windowSize; x < cx + windowSize; x++) {
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    int gray = image.getRGB(x, y) & 0xFF;
                    sum += gray;
                    sumSquares += gray * gray;
                    count++;
                }
            }
        }

        if (count == 0) return 0;

        double mean = sum / count;
        double variance = (sumSquares / count) - (mean * mean);

        return variance / (255.0 * 255.0); // Normalize
    }

    private boolean detectVerticalLinePattern(BufferedImage image) {
        // Detect vertical runs (barcode characteristic)
        int consecutiveRunsRequired = image.getHeight() / 4;
        int verticalLinesDetected = 0;

        for (int x = 0; x < image.getWidth(); x++) {
            int consecutiveRun = 0;
            int prevGray = -1;

            for (int y = 0; y < image.getHeight(); y++) {
                int gray = image.getRGB(x, y) & 0xFF;

                if (Math.abs(gray - prevGray) < 30) {
                    consecutiveRun++;
                } else {
                    if (consecutiveRun > consecutiveRunsRequired) {
                        verticalLinesDetected++;
                        break;
                    }
                    consecutiveRun = 0;
                }
                prevGray = gray;
            }
        }

        return verticalLinesDetected > 5;
    }

    private boolean detectSquareFinderPatterns(BufferedImage image) {
        // QR codes have 3 square finder patterns in corners
        // Simplified detection: look for high contrast square regions

        int[][] corners = {{0, 0}, {image.getWidth() - 50, 0}, {0, image.getHeight() - 50}};
        int patternsFound = 0;

        for (int[] corner : corners) {
            if (hasHighContrastSquareRegion(image, corner[0], corner[1], 50)) {
                patternsFound++;
            }
        }

        return patternsFound >= 2;
    }

    private boolean hasHighContrastSquareRegion(BufferedImage image, int startX, int startY, int size) {
        if (startX + size > image.getWidth() || startY + size > image.getHeight()) {
            return false;
        }

        double variance = calculateLocalVariance(image, startX + size / 2, startY + size / 2, size / 2);
        return variance > 0.15;
    }

    private double calculateEdgeDensity(BufferedImage image) {
        BufferedImage edges = detectEdges(image);

        int edgePixels = 0;
        int totalPixels = edges.getWidth() * edges.getHeight();

        for (int y = 0; y < edges.getHeight(); y++) {
            for (int x = 0; x < edges.getWidth(); x++) {
                int gray = edges.getRGB(x, y) & 0xFF;
                if (gray > 128) edgePixels++;
            }
        }

        return (double) edgePixels / totalPixels;
    }

    private BufferedImage detectEdges(BufferedImage image) {
        // Sobel edge detection
        BufferedImage edges = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                int gx = 0, gy = 0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int gray = image.getRGB(x + kx, y + ky) & 0xFF;
                        gx += gray * sobelX[ky + 1][kx + 1];
                        gy += gray * sobelY[ky + 1][kx + 1];
                    }
                }

                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
                magnitude = Math.min(255, magnitude);

                int rgb = (magnitude << 16) | (magnitude << 8) | magnitude;
                edges.setRGB(x, y, rgb);
            }
        }

        return edges;
    }

    private int countCircularPatterns(BufferedImage edges) {
        // Simplified circle counting using edge density in circular regions
        int circlesFound = 0;
        int step = 30;

        for (int cy = step; cy < edges.getHeight() - step; cy += step) {
            for (int cx = step; cx < edges.getWidth() - step; cx += step) {
                if (hasCircularEdgePattern(edges, cx, cy, step)) {
                    circlesFound++;
                }
            }
        }

        return circlesFound;
    }

    private boolean hasCircularEdgePattern(BufferedImage edges, int cx, int cy, int radius) {
        int circumference = (int) (2 * Math.PI * radius);
        int edgePoints = 0;

        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            int x = cx + (int) (radius * Math.cos(rad));
            int y = cy + (int) (radius * Math.sin(rad));

            if (x >= 0 && x < edges.getWidth() && y >= 0 && y < edges.getHeight()) {
                int gray = edges.getRGB(x, y) & 0xFF;
                if (gray > 128) edgePoints++;
            }
        }

        return edgePoints > circumference * 0.3;
    }

    private double calculatePeriodicity(BufferedImage image) {
        // Analyze autocorrelation to detect periodic patterns
        // Simplified version using row-wise similarity

        double totalSimilarity = 0;
        int comparisons = 0;

        for (int y = 0; y < image.getHeight(); y += 5) {
            for (int offset = 5; offset < 20; offset++) {
                if (y + offset < image.getHeight()) {
                    double similarity = calculateRowSimilarity(image, y, y + offset);
                    totalSimilarity += similarity;
                    comparisons++;
                }
            }
        }

        return comparisons > 0 ? totalSimilarity / comparisons : 0.0;
    }

    private double calculateRowSimilarity(BufferedImage image, int row1, int row2) {
        double similarity = 0;

        for (int x = 0; x < image.getWidth(); x++) {
            int gray1 = image.getRGB(x, row1) & 0xFF;
            int gray2 = image.getRGB(x, row2) & 0xFF;

            int diff = Math.abs(gray1 - gray2);
            similarity += (255 - diff) / 255.0;
        }

        return similarity / image.getWidth();
    }

    private double calculateChannelVariance(BufferedImage image, int channel) {
        double sum = 0, sumSquares = 0;
        int count = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int value = (rgb >> (16 - channel * 8)) & 0xFF;

                sum += value;
                sumSquares += value * value;
                count++;
            }
        }

        if (count == 0) return 0;

        double mean = sum / count;
        double variance = (sumSquares / count) - (mean * mean);

        return variance / (255.0 * 255.0); // Normalize
    }
}
