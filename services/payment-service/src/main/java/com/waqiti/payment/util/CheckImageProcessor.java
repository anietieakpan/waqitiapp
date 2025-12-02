package com.waqiti.payment.util;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for processing check images and extracting data
 */
@Component
@Slf4j
public class CheckImageProcessor {
    
    private static final int MIN_IMAGE_WIDTH = 800;
    private static final int MIN_IMAGE_HEIGHT = 400;
    private static final double MIN_CONTRAST_RATIO = 0.5;
    private static final double MAX_BLUR_SCORE = 0.3;
    
    // Amount patterns
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)"
    );
    
    private static final Pattern WRITTEN_AMOUNT_PATTERN = Pattern.compile(
        "([A-Za-z\\s]+)\\s+(?:and|&)\\s+(\\d{1,2})/100"
    );
    
    // Date patterns
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy"),
        DateTimeFormatter.ofPattern("MMMM dd, yyyy")
    );
    
    /**
     * Analyzes image quality
     */
    public ImageQualityResult analyzeImageQuality(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            
            if (image == null) {
                return ImageQualityResult.builder()
                    .acceptable(false)
                    .reason("Unable to read image")
                    .build();
            }
            
            // Check dimensions
            if (image.getWidth() < MIN_IMAGE_WIDTH || image.getHeight() < MIN_IMAGE_HEIGHT) {
                return ImageQualityResult.builder()
                    .acceptable(false)
                    .reason("Image resolution too low")
                    .resolution(image.getWidth() + "x" + image.getHeight())
                    .build();
            }
            
            // Check contrast
            double contrastRatio = calculateContrast(image);
            if (contrastRatio < MIN_CONTRAST_RATIO) {
                return ImageQualityResult.builder()
                    .acceptable(false)
                    .reason("Poor image contrast")
                    .contrastRatio(contrastRatio)
                    .build();
            }
            
            // Check blur
            double blurScore = calculateBlurScore(image);
            if (blurScore > MAX_BLUR_SCORE) {
                return ImageQualityResult.builder()
                    .acceptable(false)
                    .reason("Image is too blurry")
                    .blurScore(blurScore)
                    .build();
            }
            
            // Check for skew
            double skewAngle = detectSkew(image);
            if (Math.abs(skewAngle) > 5.0) {
                return ImageQualityResult.builder()
                    .acceptable(false)
                    .reason("Image is too skewed")
                    .skewAngle(skewAngle)
                    .build();
            }
            
            return ImageQualityResult.builder()
                .acceptable(true)
                .reason("Image quality acceptable")
                .resolution(image.getWidth() + "x" + image.getHeight())
                .contrastRatio(contrastRatio)
                .blurScore(blurScore)
                .skewAngle(skewAngle)
                .build();
                
        } catch (Exception e) {
            log.error("Error analyzing image quality", e);
            return ImageQualityResult.builder()
                .acceptable(false)
                .reason("Error processing image")
                .build();
        }
    }
    
    /**
     * Extracts amount from check image
     */
    public AmountExtractionResult extractAmount(byte[] imageData) {
        try {
            // In production, this would use OCR
            String ocrText = performOCR(imageData);
            
            // Look for numeric amount
            BigDecimal numericAmount = extractNumericAmount(ocrText);
            
            // Look for written amount
            BigDecimal writtenAmount = extractWrittenAmount(ocrText);
            
            // Compare and validate
            if (numericAmount != null && writtenAmount != null) {
                if (numericAmount.compareTo(writtenAmount) == 0) {
                    return AmountExtractionResult.builder()
                        .amount(numericAmount)
                        .confidence(new BigDecimal("0.95"))
                        .numericAmount(numericAmount)
                        .writtenAmount(writtenAmount)
                        .build();
                } else {
                    // Amounts don't match
                    return AmountExtractionResult.builder()
                        .amount(numericAmount) // Use numeric as primary
                        .confidence(new BigDecimal("0.5"))
                        .numericAmount(numericAmount)
                        .writtenAmount(writtenAmount)
                        .mismatch(true)
                        .build();
                }
            } else if (numericAmount != null) {
                return AmountExtractionResult.builder()
                    .amount(numericAmount)
                    .confidence(new BigDecimal("0.7"))
                    .numericAmount(numericAmount)
                    .build();
            } else if (writtenAmount != null) {
                return AmountExtractionResult.builder()
                    .amount(writtenAmount)
                    .confidence(new BigDecimal("0.6"))
                    .writtenAmount(writtenAmount)
                    .build();
            }
            
            return AmountExtractionResult.builder()
                .confidence(new BigDecimal("0.0"))
                .build();
                
        } catch (Exception e) {
            log.error("Error extracting amount", e);
            return AmountExtractionResult.builder()
                .confidence(new BigDecimal("0.0"))
                .build();
        }
    }
    
    /**
     * Extracts check details
     */
    public CheckDetails extractCheckDetails(byte[] imageData) {
        try {
            String ocrText = performOCR(imageData);
            
            return CheckDetails.builder()
                .payeeName(extractPayeeName(ocrText))
                .payorName(extractPayorName(ocrText))
                .checkDate(extractDate(ocrText))
                .memo(extractMemo(ocrText))
                .bankName(extractBankName(ocrText))
                .build();
                
        } catch (Exception e) {
            log.error("Error extracting check details", e);
            return CheckDetails.builder().build();
        }
    }
    
    /**
     * Calculates image contrast
     */
    private double calculateContrast(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        double minLuminance = 255.0;
        double maxLuminance = 0.0;
        
        // Sample pixels to calculate contrast
        int sampleStep = Math.max(1, Math.min(width, height) / 100);
        
        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Calculate relative luminance using standard formula
                double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                
                minLuminance = Math.min(minLuminance, luminance);
                maxLuminance = Math.max(maxLuminance, luminance);
            }
        }
        
        // Calculate Michelson contrast
        if (maxLuminance + minLuminance > 0) {
            return (maxLuminance - minLuminance) / (maxLuminance + minLuminance);
        }
        
        return 0.0;
    }
    
    /**
     * Calculates blur score
     */
    private double calculateBlurScore(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Apply Laplacian kernel for edge detection
        double[][] laplacian = {
            {0, -1, 0},
            {-1, 4, -1},
            {0, -1, 0}
        };
        
        double variance = 0.0;
        int count = 0;
        
        // Sample the image for performance
        int step = Math.max(1, Math.min(width, height) / 50);
        
        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                double sum = 0.0;
                
                // Apply kernel
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int rgb = image.getRGB(x + kx, y + ky);
                        int gray = (int)(0.299 * ((rgb >> 16) & 0xFF) + 
                                        0.587 * ((rgb >> 8) & 0xFF) + 
                                        0.114 * (rgb & 0xFF));
                        sum += gray * laplacian[ky + 1][kx + 1];
                    }
                }
                
                variance += sum * sum;
                count++;
            }
        }
        
        if (count > 0) {
            variance /= count;
            // Normalize to 0-1 range (lower is blurrier)
            return 1.0 / (1.0 + Math.sqrt(variance) / 100.0);
        }
        
        return 1.0; // Maximum blur
    }
    
    /**
     * Detects image skew angle
     */
    private double detectSkew(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Use simplified Hough transform to detect dominant lines
        int maxAngle = 45;
        int angleStep = 1;
        int[] accumulator = new int[2 * maxAngle / angleStep + 1];
        
        // Sample edges for line detection
        int step = Math.max(1, Math.min(width, height) / 100);
        
        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                // Check if this is an edge pixel
                if (isEdgePixel(image, x, y)) {
                    // Vote for all possible lines through this point
                    for (int angle = -maxAngle; angle <= maxAngle; angle += angleStep) {
                        double theta = Math.toRadians(angle);
                        int index = (angle + maxAngle) / angleStep;
                        accumulator[index]++;
                    }
                }
            }
        }
        
        // Find the angle with the most votes
        int maxVotes = 0;
        int dominantAngle = 0;
        
        for (int i = 0; i < accumulator.length; i++) {
            if (accumulator[i] > maxVotes) {
                maxVotes = accumulator[i];
                dominantAngle = -maxAngle + i * angleStep;
            }
        }
        
        // Return the skew angle (positive = clockwise)
        return dominantAngle;
    }
    
    /**
     * Helper method to detect edge pixels
     */
    private boolean isEdgePixel(BufferedImage image, int x, int y) {
        if (x <= 0 || x >= image.getWidth() - 1 || y <= 0 || y >= image.getHeight() - 1) {
            return false;
        }
        
        // Sobel edge detection
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
        
        int gx = 0, gy = 0;
        
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int rgb = image.getRGB(x + dx, y + dy);
                int gray = (int)(0.299 * ((rgb >> 16) & 0xFF) + 
                                0.587 * ((rgb >> 8) & 0xFF) + 
                                0.114 * (rgb & 0xFF));
                
                gx += gray * sobelX[dy + 1][dx + 1];
                gy += gray * sobelY[dy + 1][dx + 1];
            }
        }
        
        // Calculate gradient magnitude
        double magnitude = Math.sqrt(gx * gx + gy * gy);
        
        // Threshold for edge detection
        return magnitude > 50;
    }
    
    /**
     * Performs OCR on image
     */
    private String performOCR(byte[] imageData) {
        // In production, integrate with OCR service
        // This is a simulation for testing
        return simulateOCRText();
    }
    
    /**
     * Extracts numeric amount from text
     */
    private BigDecimal extractNumericAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("No text provided for numeric amount extraction");
            throw new CheckProcessingException("No text provided for numeric amount extraction");
        }
        
        log.debug("Extracting numeric amount from text: {}", text.substring(0, Math.min(text.length(), 100)));
        
        // Try multiple patterns for different amount formats
        Pattern[] amountPatterns = {
            Pattern.compile("\\$\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)"), // $1,234.56
            Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)\\s*(?:DOLLARS|Dollars|dollars)?"), // 1,234.56 DOLLARS
            Pattern.compile("USD\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)"), // USD 1,234.56
            Pattern.compile("([0-9]+\\.[0-9]{2})(?!\\d)"), // Simple decimal format
            Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)(?!\\d)") // Just comma-separated numbers
        };
        
        for (Pattern pattern : amountPatterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String amountStr = matcher.group(1).replaceAll(",", "");
                try {
                    BigDecimal amount = new BigDecimal(amountStr);
                    // Validate amount is reasonable for a check (between $0.01 and $1,000,000)
                    if (amount.compareTo(new BigDecimal("0.01")) >= 0 && 
                        amount.compareTo(new BigDecimal("1000000")) <= 0) {
                        log.debug("Successfully extracted numeric amount: {}", amount);
                        return amount;
                    } else {
                        log.debug("Amount {} outside reasonable range, continuing search", amount);
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse amount string: {}", amountStr, e);
                }
            }
        }
        
        log.debug("No valid numeric amount found in text");
        throw new CheckProcessingException("No valid numeric amount found in check image text");
    }
    
    /**
     * Extracts written amount from text
     */
    private BigDecimal extractWrittenAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("No text provided for written amount extraction");
            throw new CheckProcessingException("No text provided for written amount extraction");
        }
        
        log.debug("Extracting written amount from text");
        
        // Enhanced pattern for written amounts with cents
        Pattern[] writtenPatterns = {
            Pattern.compile("([A-Za-z\\s]+)\\s+(?:and|&)\\s+(\\d{1,2})/100", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Za-z\\s]+)\\s+(?:DOLLARS|Dollars|dollars)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Za-z\\s]+)\\s+(?:and|&)\\s+(\\d{1,2})\\s*(?:cents|CENTS)", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern pattern : writtenPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String dollarsText = matcher.group(1).trim();
                String cents = matcher.groupCount() > 1 ? matcher.group(2) : "0";
                
                log.debug("Found written amount pattern - dollars: '{}', cents: '{}'", dollarsText, cents);
                
                // Convert written dollars to number
                BigDecimal dollars = convertWrittenToNumber(dollarsText);
                if (dollars != null) {
                    try {
                        BigDecimal centsValue = new BigDecimal(cents).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                        BigDecimal totalAmount = dollars.add(centsValue);
                        
                        // Validate amount is reasonable
                        if (totalAmount.compareTo(new BigDecimal("0.01")) >= 0 && 
                            totalAmount.compareTo(new BigDecimal("1000000")) <= 0) {
                            log.debug("Successfully extracted written amount: {}", totalAmount);
                            return totalAmount;
                        }
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse cents value: {}", cents, e);
                    }
                }
            }
        }
        
        // Try to extract just dollar amounts without cents
        BigDecimal dollarAmount = convertWrittenToNumber(text);
        if (dollarAmount != null && dollarAmount.compareTo(BigDecimal.ZERO) > 0) {
            log.debug("Extracted dollar-only written amount: {}", dollarAmount);
            return dollarAmount;
        }
        
        log.debug("No valid written amount found in text");
        throw new CheckProcessingException("No valid written amount found in check image text");
    }
    
    /**
     * Converts written number to BigDecimal
     */
    private BigDecimal convertWrittenToNumber(String written) {
        if (written == null || written.trim().isEmpty()) {
            throw new CheckProcessingException("No written text provided for conversion");
        }
        
        written = written.toLowerCase().trim().replaceAll("[^a-z\\s]", "");
        log.debug("Converting written number: '{}'", written);
        
        // Handle special cases first
        if (written.matches("^\\s*$")) {
            throw new CheckProcessingException("Written amount text contains only whitespace");
        }
        
        if (written.contains("zero") && !written.contains("thousand") && !written.contains("hundred")) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal result = BigDecimal.ZERO;
        BigDecimal currentNumber = BigDecimal.ZERO;
        
        // Split by "and" to separate whole dollars from cents description
        String[] parts = written.split("\\s+and\\s+");
        String mainPart = parts[0].trim();
        
        // Process the main dollar amount
        String[] words = mainPart.split("\\s+");
        
        for (int i = 0; i < words.length; i++) {
            String word = words[i].trim();
            BigDecimal wordValue = getWordValue(word);
            
            if (wordValue != null) {
                if (wordValue.equals(new BigDecimal("100"))) { // hundred
                    if (currentNumber.equals(BigDecimal.ZERO)) {
                        currentNumber = new BigDecimal("100");
                    } else {
                        currentNumber = currentNumber.multiply(new BigDecimal("100"));
                    }
                } else if (wordValue.equals(new BigDecimal("1000"))) { // thousand
                    if (currentNumber.equals(BigDecimal.ZERO)) {
                        currentNumber = new BigDecimal("1000");
                    } else {
                        result = result.add(currentNumber.multiply(new BigDecimal("1000")));
                        currentNumber = BigDecimal.ZERO;
                    }
                } else if (wordValue.equals(new BigDecimal("1000000"))) { // million
                    if (currentNumber.equals(BigDecimal.ZERO)) {
                        currentNumber = new BigDecimal("1000000");
                    } else {
                        result = result.add(currentNumber.multiply(new BigDecimal("1000000")));
                        currentNumber = BigDecimal.ZERO;
                    }
                } else {
                    currentNumber = currentNumber.add(wordValue);
                }
            }
        }
        
        result = result.add(currentNumber);
        
        if (result.compareTo(BigDecimal.ZERO) > 0) {
            log.debug("Successfully converted '{}' to {}", written, result);
            return result;
        }
        
        log.debug("Could not convert written number: '{}'", written);
        throw new CheckProcessingException("Unable to convert written number to decimal: " + written);
    }
    
    /**
     * Gets the numeric value for a written number word
     */
    private BigDecimal getWordValue(String word) {
        return switch (word.toLowerCase()) {
            case "zero" -> BigDecimal.ZERO;
            case "one" -> new BigDecimal("1");
            case "two" -> new BigDecimal("2");
            case "three" -> new BigDecimal("3");
            case "four" -> new BigDecimal("4");
            case "five" -> new BigDecimal("5");
            case "six" -> new BigDecimal("6");
            case "seven" -> new BigDecimal("7");
            case "eight" -> new BigDecimal("8");
            case "nine" -> new BigDecimal("9");
            case "ten" -> new BigDecimal("10");
            case "eleven" -> new BigDecimal("11");
            case "twelve" -> new BigDecimal("12");
            case "thirteen" -> new BigDecimal("13");
            case "fourteen" -> new BigDecimal("14");
            case "fifteen" -> new BigDecimal("15");
            case "sixteen" -> new BigDecimal("16");
            case "seventeen" -> new BigDecimal("17");
            case "eighteen" -> new BigDecimal("18");
            case "nineteen" -> new BigDecimal("19");
            case "twenty" -> new BigDecimal("20");
            case "thirty" -> new BigDecimal("30");
            case "forty" -> new BigDecimal("40");
            case "fifty" -> new BigDecimal("50");
            case "sixty" -> new BigDecimal("60");
            case "seventy" -> new BigDecimal("70");
            case "eighty" -> new BigDecimal("80");
            case "ninety" -> new BigDecimal("90");
            case "hundred" -> new BigDecimal("100");
            case "thousand" -> new BigDecimal("1000");
            case "million" -> new BigDecimal("1000000");
            default -> null;
        };
    }
    
    /**
     * Extracts payee name
     */
    private String extractPayeeName(String text) {
        // Look for "Pay to the order of" pattern
        Pattern pattern = Pattern.compile("Pay to the order of\\s+([A-Za-z\\s]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        throw new CheckProcessingException("No payee name found in check image text");
    }
    
    /**
     * Extracts payor name
     */
    private String extractPayorName(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("No text provided for payor name extraction");
            throw new CheckProcessingException("No text provided for payor name extraction");
        }
        
        log.debug("Extracting payor name from text");
        
        // Look for patterns typically found in the top-left area of checks
        Pattern[] payorPatterns = {
            Pattern.compile("^([A-Z][a-zA-Z\\s\\.]{2,30})\\s*$", Pattern.MULTILINE),
            Pattern.compile("([A-Z][a-zA-Z\\s\\.]{2,30})\\s+\\d{1,5}\\s+[A-Z][a-zA-Z\\s]+"), // Name followed by address
            Pattern.compile("([A-Z][a-zA-Z\\s\\.]{2,30})\\s*\\n.*(?:Street|St|Avenue|Ave|Road|Rd|Lane|Ln|Drive|Dr|Boulevard|Blvd)"),
            Pattern.compile("From:\\s*([A-Z][a-zA-Z\\s\\.]{2,30})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Account\\s+Holder:\\s*([A-Z][a-zA-Z\\s\\.]{2,30})", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern pattern : payorPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String payorName = matcher.group(1).trim();
                // Validate it looks like a person or business name
                if (payorName.length() >= 3 && payorName.length() <= 50 && 
                    !payorName.toLowerCase().matches(".*(bank|credit|union|corp|inc|ltd|llc).*") &&
                    payorName.matches("[A-Z][a-zA-Z\\s\\.]+")) {
                    log.debug("Extracted payor name: {}", payorName);
                    return payorName;
                }
            }
        }
        
        // Fallback: Look for lines that might contain names in the first few lines
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            if (line.matches("[A-Z][a-zA-Z\\s\\.]{2,30}") && 
                !line.toLowerCase().contains("pay to") &&
                !line.toLowerCase().contains("date") &&
                !line.toLowerCase().matches(".*(\\$|dollar|cent).*")) {
                log.debug("Extracted payor name from line {}: {}", i, line);
                return line;
            }
        }
        
        log.debug("No payor name found in text");
        throw new CheckProcessingException("No payor name found in check image text");
    }
    
    /**
     * Extracts date from text
     */
    private LocalDate extractDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("No text provided for date extraction");
            throw new CheckProcessingException("No text provided for date extraction");
        }
        
        log.debug("Extracting date from text");
        
        // Enhanced date patterns for different formats commonly found on checks
        Pattern[] datePatterns = {
            Pattern.compile("(?:Date:?\\s*)?([01]?\\d)[/\\-]([0123]?\\d)[/\\-](\\d{4})"),
            Pattern.compile("(?:Date:?\\s*)?([01]?\\d)[/\\-]([0123]?\\d)[/\\-](\\d{2})"),
            Pattern.compile("(?:Date:?\\s*)?(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[,\\s]+(\\d{4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:Date:?\\s*)?(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})[,\\s]+(\\d{4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([01]?\\d)[/\\-]([0123]?\\d)[/\\-](\\d{4})\\b"), // Anywhere in text
            Pattern.compile("([01]?\\d)[/\\-]([0123]?\\d)[/\\-](\\d{2})\\b") // 2-digit year
        };
        
        for (Pattern pattern : datePatterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                try {
                    LocalDate extractedDate = null;
                    
                    if (pattern.pattern().contains("Jan|Feb|Mar")) {
                        // Short month format
                        String day = matcher.group(1);
                        String monthStr = matcher.group(2);
                        String year = matcher.group(3);
                        int month = parseMonth(monthStr);
                        if (month > 0) {
                            extractedDate = LocalDate.of(Integer.parseInt(year), month, Integer.parseInt(day));
                        }
                    } else if (pattern.pattern().contains("January|February")) {
                        // Full month format
                        String monthStr = matcher.group(1);
                        String day = matcher.group(2);
                        String year = matcher.group(3);
                        int month = parseMonth(monthStr);
                        if (month > 0) {
                            extractedDate = LocalDate.of(Integer.parseInt(year), month, Integer.parseInt(day));
                        }
                    } else {
                        // Numeric format MM/DD/YYYY or MM/DD/YY
                        String monthStr = matcher.group(1);
                        String dayStr = matcher.group(2);
                        String yearStr = matcher.group(3);
                        
                        int month = Integer.parseInt(monthStr);
                        int day = Integer.parseInt(dayStr);
                        int year = Integer.parseInt(yearStr);
                        
                        // Handle 2-digit years
                        if (year < 100) {
                            year += (year < 30) ? 2000 : 1900;
                        }
                        
                        extractedDate = LocalDate.of(year, month, day);
                    }
                    
                    // Validate the date is reasonable for a check
                    if (extractedDate != null && isReasonableCheckDate(extractedDate)) {
                        log.debug("Extracted check date: {}", extractedDate);
                        return extractedDate;
                    }
                    
                } catch (Exception e) {
                    log.debug("Failed to parse date from match: {}", matcher.group(), e);
                }
            }
        }
        
        log.debug("No valid date found in text");
        throw new CheckProcessingException("No valid date found in check image text");
    }
    
    /**
     * Parses month name to number
     */
    private int parseMonth(String monthName) {
        return switch (monthName.toLowerCase().substring(0, 3)) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> -1;
        };
    }
    
    /**
     * Validates if a date is reasonable for a check
     */
    private boolean isReasonableCheckDate(LocalDate date) {
        LocalDate now = LocalDate.now();
        LocalDate fiveYearsAgo = now.minusYears(5);
        LocalDate oneYearFuture = now.plusYears(1);
        
        return date.isAfter(fiveYearsAgo) && date.isBefore(oneYearFuture);
    }
    
    /**
     * Extracts memo
     */
    private String extractMemo(String text) {
        Pattern pattern = Pattern.compile("(?:Memo|For):\\s*(.+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        throw new CheckProcessingException("No memo found in check image text");
    }
    
    /**
     * Extracts bank name
     */
    private String extractBankName(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("No text provided for bank name extraction");
            throw new CheckProcessingException("No text provided for bank name extraction");
        }
        
        log.debug("Extracting bank name from text");
        
        // Common bank name patterns and keywords
        Pattern[] bankPatterns = {
            Pattern.compile("([A-Z][a-zA-Z\\s]+(?:Bank|BANK))(?:\\s+(?:N\\.?A\\.?|National Association))?"),
            Pattern.compile("([A-Z][a-zA-Z\\s]+(?:Credit Union|CREDIT UNION))"),
            Pattern.compile("([A-Z][a-zA-Z\\s]+(?:Federal Credit Union|FCU))"),
            Pattern.compile("((?:First|Second|Third|Fourth|Fifth)\\s+[A-Z][a-zA-Z\\s]+Bank)"),
            Pattern.compile("([A-Z][a-zA-Z\\s]+(?:Trust|TRUST)(?:\\s+Company)?)"),
            Pattern.compile("([A-Z][a-zA-Z\\s]+(?:Savings|SAVINGS)(?:\\s+(?:Bank|Association))?)"),
            Pattern.compile("((?:Wells Fargo|Bank of America|Chase|Citibank|US Bank|PNC Bank|Capital One|TD Bank|SunTrust|BB&T|Regions Bank|KeyBank|Fifth Third|Huntington|M&T Bank))\\b", Pattern.CASE_INSENSITIVE)
        };
        
        // Known major bank abbreviations and full names
        String[] knownBanks = {
            "Wells Fargo", "Bank of America", "JPMorgan Chase", "Citibank", "US Bank",
            "PNC Bank", "Capital One", "TD Bank", "SunTrust", "BB&T", "Regions Bank",
            "KeyBank", "Fifth Third Bank", "Huntington Bank", "M&T Bank", "Navy Federal",
            "USAA", "Charles Schwab Bank", "Ally Bank", "Discover Bank"
        };
        
        // First try specific known banks
        for (String bankName : knownBanks) {
            if (text.toLowerCase().contains(bankName.toLowerCase())) {
                log.debug("Found known bank: {}", bankName);
                return bankName;
            }
        }
        
        // Then try pattern matching
        for (Pattern pattern : bankPatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String extractedBank = matcher.group(1).trim();
                // Validate the extracted bank name
                if (extractedBank.length() >= 5 && extractedBank.length() <= 50 &&
                    !extractedBank.toLowerCase().matches(".*(pay to|date|memo|amount|dollars).*")) {
                    log.debug("Extracted bank name: {}", extractedBank);
                    return extractedBank;
                }
            }
        }
        
        // Fallback: Look for lines containing bank-related keywords
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.toLowerCase().matches(".*\\b(bank|credit union|trust|savings)\\b.*") &&
                line.length() >= 5 && line.length() <= 50 &&
                !line.toLowerCase().matches(".*(pay to|date|memo|amount|dollars).*")) {
                // Clean up common OCR artifacts
                String cleaned = line.replaceAll("[^a-zA-Z\\s&]", "").trim();
                if (cleaned.length() >= 5) {
                    log.debug("Extracted bank name from line: {}", cleaned);
                    return cleaned;
                }
            }
        }
        
        log.debug("No bank name found in text");
        throw new CheckProcessingException("No bank name found in check image text");
    }
    
    /**
     * Simulates OCR text for testing
     */
    private String simulateOCRText() {
        return """
            Pay to the order of John Doe
            $1,234.56
            One thousand two hundred thirty four and 56/100
            Date: 12/15/2023
            Memo: Rent payment
            Bank of America
            """;
    }
    
    /**
     * Image quality result
     */
    @Data
    @Builder
    public static class ImageQualityResult {
        private boolean acceptable;
        private String reason;
        private String resolution;
        private Double contrastRatio;
        private Double blurScore;
        private Double skewAngle;
    }
    
    /**
     * Amount extraction result
     */
    @Data
    @Builder
    public static class AmountExtractionResult {
        private BigDecimal amount;
        private BigDecimal confidence;
        private BigDecimal numericAmount;
        private BigDecimal writtenAmount;
        private boolean mismatch;
    }
    
    /**
     * Check details
     */
    @Data
    @Builder
    public static class CheckDetails {
        private String payeeName;
        private String payorName;
        private LocalDate checkDate;
        private String memo;
        private String bankName;
    }
}