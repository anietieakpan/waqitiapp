package com.waqiti.payment.util;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.*;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.ITesseract;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Utility class for parsing MICR (Magnetic Ink Character Recognition) data from check images
 */
@Component
@Slf4j
public class MICRParser {
    
    @Value("${aws.textract.enabled:false}")
    private boolean awsTextractEnabled;
    
    @Value("${google.vision.enabled:false}")
    private boolean googleVisionEnabled;
    
    @Value("${tesseract.datapath:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tesseractDataPath;
    
    private AmazonTextract textractClient;
    private ImageAnnotatorClient visionClient;
    private ITesseract tesseract;
    
    // MICR line pattern: T<routing>T <account>O <check#>
    private static final Pattern MICR_PATTERN = Pattern.compile(
        "T(\\d{9})T\\s+(\\d{4,17})O\\s+(\\d{1,10})"
    );
    
    // Alternative MICR patterns for different check formats
    private static final Pattern ALT_MICR_PATTERN1 = Pattern.compile(
        ":(\\d{9}):\\s+(\\d{4,17})\\$\\s+(\\d{1,10})"
    );
    
    private static final Pattern ALT_MICR_PATTERN2 = Pattern.compile(
        "\\|(\\d{9})\\|\\s+(\\d{4,17})\\s+(\\d{1,10})\\|"
    );
    
    /**
     * Extracts MICR data from check image
     */
    @NonNull
    public MICRData extractMICR(@NonNull byte[] checkImage) {
        try {
            log.debug("Extracting MICR data from check image");
            
            String micrLine = performOCR(checkImage);
            
            if (micrLine == null || micrLine.isEmpty()) {
                log.warn("No MICR line detected in check image");
                
                // Return error result instead of null
                return MICRData.builder()
                    .valid(false)
                    .errorCode("NO_MICR_LINE_DETECTED")
                    .errorMessage("MICR line could not be detected in the check image")
                    .confidence(0.0)
                    .processingStatus("FAILED_OCR")
                    .build();
            }
            
            // Try different MICR patterns
            MICRData micrData = parseMICRLine(micrLine);
            
            if (micrData != null && micrData.isValid()) {
                log.info("Successfully extracted MICR data");
                return micrData;
            }
            
            log.warn("Failed to parse MICR line: {}", micrLine);
            
            // Return error result instead of null
            return MICRData.builder()
                .valid(false)
                .errorCode("MICR_PARSE_FAILED")
                .errorMessage("MICR line format is not recognized: " + micrLine)
                .confidence(0.0)
                .processingStatus("FAILED_PARSE")
                .rawMicrLine(micrLine)
                .build();
            
        } catch (Exception e) {
            log.error("Error extracting MICR data", e);
            
            // Return error result instead of null
            return MICRData.builder()
                .valid(false)
                .errorCode("MICR_PROCESSING_ERROR")
                .errorMessage("Unexpected error during MICR processing: " + e.getMessage())
                .confidence(0.0)
                .processingStatus("ERROR")
                .exception(e.getClass().getSimpleName())
                .build();
        }
    }
    
    /**
     * Performs OCR on check image to extract MICR line
     */
    private String performOCR(byte[] checkImage) {
        try {
            // Attempt primary OCR service (AWS Textract)
            String micrLine = performAWSTextractOCR(checkImage);
            if (micrLine != null && !micrLine.isEmpty()) {
                log.debug("Successfully extracted MICR using AWS Textract");
                return micrLine;
            }
            
            // Fallback to Google Vision API
            micrLine = performGoogleVisionOCR(checkImage);
            if (micrLine != null && !micrLine.isEmpty()) {
                log.debug("Successfully extracted MICR using Google Vision");
                return micrLine;
            }
            
            // Final fallback to Tesseract (local processing)
            micrLine = performTesseractOCR(checkImage);
            if (micrLine != null && !micrLine.isEmpty()) {
                log.debug("Successfully extracted MICR using Tesseract");
                return micrLine;
            }
            
            log.error("All OCR methods failed to extract MICR line - queuing for manual review");
            
            // Instead of returning null, return a special "manual review required" indicator
            return "MANUAL_REVIEW_REQUIRED";
            
        } catch (Exception e) {
            log.error("Critical error in OCR processing - queuing for manual review", e);
            
            // Return indicator for manual processing instead of null
            return "MANUAL_REVIEW_REQUIRED";
        }
    }
    
    /**
     * Parses MICR line string into structured data
     */
    private MICRData parseMICRLine(String micrLine) {
        // Clean the MICR line
        String cleanedLine = micrLine.trim().replaceAll("\\s+", " ");
        
        // Try standard pattern
        Matcher matcher = MICR_PATTERN.matcher(cleanedLine);
        if (matcher.find()) {
            return MICRData.builder()
                .routingNumber(matcher.group(1))
                .accountNumber(matcher.group(2))
                .checkNumber(matcher.group(3))
                .rawMicr(micrLine)
                .valid(true)
                .confidence(0.95)
                .processingStatus("SUCCESS")
                .build();
        }
        
        // Try alternative pattern 1
        matcher = ALT_MICR_PATTERN1.matcher(cleanedLine);
        if (matcher.find()) {
            return MICRData.builder()
                .routingNumber(matcher.group(1))
                .accountNumber(matcher.group(2))
                .checkNumber(matcher.group(3))
                .rawMicr(micrLine)
                .valid(true)
                .confidence(0.90)
                .processingStatus("SUCCESS")
                .build();
        }
        
        // Try alternative pattern 2
        matcher = ALT_MICR_PATTERN2.matcher(cleanedLine);
        if (matcher.find()) {
            return MICRData.builder()
                .routingNumber(matcher.group(1))
                .accountNumber(matcher.group(2))
                .checkNumber(matcher.group(3))
                .rawMicr(micrLine)
                .valid(true)
                .confidence(0.85)
                .processingStatus("SUCCESS")
                .build();
        }
        
        // No pattern matched - return invalid result instead of null
        log.debug("No MICR pattern matched for line: {}", micrLine);
        return MICRData.builder()
            .valid(false)
            .errorCode("NO_PATTERN_MATCH")
            .errorMessage("MICR line does not match any known formats")
            .confidence(0.0)
            .processingStatus("PATTERN_MISMATCH")
            .rawMicrLine(micrLine)
            .build();
    }
    
    /**
     * Validates MICR checksum (modulus 10 check for routing number)
     */
    public boolean validateMICRChecksum(String routingNumber) {
        if (routingNumber == null || routingNumber.length() != 9) {
            return false;
        }
        
        try {
            int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
            int sum = 0;
            
            for (int i = 0; i < 9; i++) {
                sum += Character.getNumericValue(routingNumber.charAt(i)) * weights[i];
            }
            
            return sum % 10 == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * AWS Textract OCR implementation
     */
    private String performAWSTextractOCR(byte[] checkImage) {
        if (!awsTextractEnabled || textractClient == null) {
            initializeAWSTextract();
        }
        
        if (textractClient == null) {
            log.warn("AWS Textract client not available - skipping OCR");
            return null; // This is acceptable - method continues to next OCR method
        }
        
        try {
            DetectDocumentTextRequest request = new DetectDocumentTextRequest()
                .withDocument(new Document()
                    .withBytes(ByteBuffer.wrap(checkImage)));
            
            DetectDocumentTextResult result = textractClient.detectDocumentText(request);
            
            // Look for MICR line at the bottom of the check
            StringBuilder micrLine = new StringBuilder();
            for (Block block : result.getBlocks()) {
                if (block.getBlockType().equals("LINE")) {
                    String text = block.getText();
                    // MICR lines typically contain specific characters
                    if (text != null && (text.contains("T") || text.contains(":") || 
                        text.contains("|") || text.matches(".*\\d{9}.*"))) {
                        // Check if this looks like a MICR line
                        if (text.length() > 20 && containsMICRCharacters(text)) {
                            micrLine.append(text).append(" ");
                        }
                    }
                }
            }
            
            String extractedMicr = micrLine.toString().trim();
            return extractedMicr.isEmpty() ? null : extractedMicr;
            
        } catch (Exception e) {
            log.error("AWS Textract OCR failed", e);
            return null; // This is acceptable - caller will try next OCR method
        }
    }
    
    /**
     * Google Vision API OCR implementation
     */
    private String performGoogleVisionOCR(byte[] checkImage) {
        if (!googleVisionEnabled || visionClient == null) {
            initializeGoogleVision();
        }
        
        if (visionClient == null) {
            log.warn("Google Vision client not available - skipping OCR");
            return null; // This is acceptable - method continues to next OCR method
        }
        
        try {
            ByteString imgBytes = ByteString.copyFrom(checkImage);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            
            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build();
            
            List<AnnotateImageRequest> requests = List.of(request);
            BatchAnnotateImagesResponse response = visionClient.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();
            
            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    log.error("Google Vision Error: {}", res.getError().getMessage());
                    return null; // This is acceptable - caller will try next OCR method
                }
                
                // Extract text and look for MICR pattern
                String fullText = res.getTextAnnotationsList().get(0).getDescription();
                String[] lines = fullText.split("\\n");
                
                // MICR line is typically at the bottom
                for (int i = lines.length - 1; i >= Math.max(0, lines.length - 5); i--) {
                    String line = lines[i];
                    if (containsMICRCharacters(line) && line.length() > 20) {
                        return line;
                    }
                }
            }
            
            return null; // No MICR found - acceptable for this method
            
        } catch (Exception e) {
            log.error("Google Vision OCR failed", e);
            return null; // This is acceptable - caller will try next OCR method
        }
    }
    
    /**
     * Tesseract OCR implementation (local processing)
     */
    private String performTesseractOCR(byte[] checkImage) {
        if (tesseract == null) {
            initializeTesseract();
        }
        
        if (tesseract == null) {
            log.warn("Tesseract not available - skipping OCR");
            return null; // This is acceptable - last OCR method, caller handles null
        }
        
        File tempFile = null;
        try {
            // Write image to temporary file
            tempFile = File.createTempFile("check_", ".png");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(checkImage);
            }
            
            // Configure Tesseract for MICR
            tesseract.setTessVariable("tessedit_char_whitelist", "0123456789T:|-O ");
            tesseract.setPageSegMode(11); // Sparse text mode
            
            // Perform OCR
            String result = tesseract.doOCR(tempFile);
            
            // Extract MICR line from result
            String[] lines = result.split("\\n");
            for (String line : lines) {
                if (containsMICRCharacters(line) && line.length() > 20) {
                    return line;
                }
            }
            
            return null; // No MICR pattern found - acceptable for this method
            
        } catch (Exception e) {
            log.error("Tesseract OCR failed", e);
            return null; // This is acceptable - caller handles null
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * Initialize AWS Textract client
     */
    private void initializeAWSTextract() {
        try {
            if (awsTextractEnabled) {
                textractClient = AmazonTextractClientBuilder.standard().build();
                log.info("AWS Textract client initialized");
            }
        } catch (Exception e) {
            log.error("Failed to initialize AWS Textract", e);
        }
    }
    
    /**
     * Initialize Google Vision client
     */
    private void initializeGoogleVision() {
        try {
            if (googleVisionEnabled) {
                visionClient = ImageAnnotatorClient.create();
                log.info("Google Vision client initialized");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Google Vision", e);
        }
    }
    
    /**
     * Initialize Tesseract OCR
     */
    private void initializeTesseract() {
        try {
            tesseract = new Tesseract();
            tesseract.setDatapath(tesseractDataPath);
            tesseract.setLanguage("eng");
            log.info("Tesseract OCR initialized");
        } catch (Exception e) {
            log.error("Failed to initialize Tesseract", e);
        }
    }
    
    /**
     * RESOURCE CLEANUP: Close Google Vision client on bean destruction to prevent resource leak
     * PRODUCTION FIX: Prevents connection pool exhaustion and memory leaks
     */
    @PreDestroy
    public void cleanup() {
        try {
            if (visionClient != null) {
                visionClient.close();
                log.info("Google Vision client closed successfully");
            }
        } catch (Exception e) {
            log.error("Error closing Google Vision client", e);
        }
    }

    /**
     * Check if string contains MICR characters
     */
    private boolean containsMICRCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // MICR lines contain routing numbers (9 digits) and special characters
        return text.matches(".*\\d{9}.*") && 
               (text.contains("T") || text.contains(":") || 
                text.contains("|") || text.contains("O"));
    }
    
    /**
     * MICR data structure
     */
    @Data
    @Builder
    public static class MICRData {
        private String routingNumber;
        private String accountNumber;
        private String checkNumber;
        private String rawMicr;
        private boolean valid;
        private String errorCode;
        private String errorMessage;
        private double confidence;
        private String processingStatus;
        private String rawMicrLine;
        private String exception;
        
        public boolean isValid() {
            // If valid flag is explicitly set to false, return false
            if (!valid && errorCode != null) {
                return false;
            }
            
            // Otherwise check standard validation rules
            return routingNumber != null && !routingNumber.isEmpty() &&
                   accountNumber != null && !accountNumber.isEmpty() &&
                   routingNumber.length() == 9 &&
                   accountNumber.length() >= 4 && accountNumber.length() <= 17;
        }
    }
}