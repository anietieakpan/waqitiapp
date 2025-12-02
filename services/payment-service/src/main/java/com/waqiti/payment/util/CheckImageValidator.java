package com.waqiti.payment.util;

import com.waqiti.common.fraud.ComprehensiveFraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Comprehensive validator for check images and extracted data
 * Implements financial industry standards for check validation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CheckImageValidator {
    
    private final ComprehensiveFraudDetectionService fraudDetectionService;
    
    // Industry standard patterns
    private static final Pattern ROUTING_NUMBER_PATTERN = Pattern.compile("^[0-9]{9}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{4,17}$");
    private static final Pattern CHECK_NUMBER_PATTERN = Pattern.compile("^[0-9]{1,4}$");
    
    // Security and fraud detection patterns
    private static final Pattern SUSPICIOUS_WORDS = Pattern.compile(
        "(?i)\\b(copy|duplicate|photocopy|scan|void|sample|specimen|test)\\b"
    );
    
    // Amount validation constraints
    private static final BigDecimal MIN_CHECK_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_CHECK_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal MOBILE_DEPOSIT_LIMIT = new BigDecimal("10000.00");
    
    // Image quality thresholds
    private static final int MIN_IMAGE_WIDTH = 800;
    private static final int MIN_IMAGE_HEIGHT = 400;
    private static final double MIN_QUALITY_SCORE = 0.7;
    private static final double MIN_CONTRAST_RATIO = 0.4;
    
    /**
     * Validates routing number according to ABA standards
     */
    public ValidationResult validateRoutingNumber(String routingNumber) {
        log.debug("Validating routing number: {}", maskRoutingNumber(routingNumber));
        
        if (routingNumber == null || routingNumber.trim().isEmpty()) {
            return ValidationResult.failure("Routing number is required");
        }
        
        String cleaned = routingNumber.replaceAll("[^0-9]", "");
        
        if (!ROUTING_NUMBER_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.failure("Routing number must be exactly 9 digits");
        }
        
        // Validate check digit using ABA algorithm
        if (!isValidABACheckDigit(cleaned)) {
            return ValidationResult.failure("Invalid routing number check digit");
        }
        
        // Check if routing number is from a known financial institution
        if (isKnownFraudulentRoutingNumber(cleaned)) {
            return ValidationResult.failure("Routing number flagged as potentially fraudulent");
        }
        
        log.debug("Routing number validation passed");
        return ValidationResult.success("Routing number is valid");
    }
    
    /**
     * Validates account number format and structure
     */
    public ValidationResult validateAccountNumber(String accountNumber) {
        log.debug("Validating account number");
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return ValidationResult.failure("Account number is required");
        }
        
        String cleaned = accountNumber.replaceAll("[^0-9]", "");
        
        if (!ACCOUNT_NUMBER_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.failure("Account number must be 4-17 digits");
        }
        
        // Check for suspicious patterns (all same digits, sequential, etc.)
        if (isSuspiciousAccountNumber(cleaned)) {
            return ValidationResult.failure("Account number appears suspicious");
        }
        
        log.debug("Account number validation passed");
        return ValidationResult.success("Account number is valid");
    }
    
    /**
     * Validates check number
     */
    public ValidationResult validateCheckNumber(String checkNumber) {
        log.debug("Validating check number: {}", checkNumber);
        
        if (checkNumber == null || checkNumber.trim().isEmpty()) {
            return ValidationResult.failure("Check number is required");
        }
        
        String cleaned = checkNumber.replaceAll("[^0-9]", "");
        
        if (!CHECK_NUMBER_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.failure("Check number must be 1-4 digits");
        }
        
        // Check number 0000 is often invalid
        if ("0000".equals(cleaned) || "000".equals(cleaned)) {
            return ValidationResult.failure("Invalid check number");
        }
        
        log.debug("Check number validation passed");
        return ValidationResult.success("Check number is valid");
    }
    
    /**
     * Validates check amount for reasonableness and limits
     */
    public ValidationResult validateAmount(BigDecimal amount, boolean isMobileDeposit) {
        log.debug("Validating amount: {}", amount);
        
        if (amount == null) {
            return ValidationResult.failure("Amount is required");
        }
        
        if (amount.compareTo(MIN_CHECK_AMOUNT) < 0) {
            return ValidationResult.failure("Amount must be at least $0.01");
        }
        
        if (amount.compareTo(MAX_CHECK_AMOUNT) > 0) {
            return ValidationResult.failure("Amount exceeds maximum limit of $100,000");
        }
        
        if (isMobileDeposit && amount.compareTo(MOBILE_DEPOSIT_LIMIT) > 0) {
            return ValidationResult.failure("Mobile deposit amount exceeds limit of $10,000");
        }
        
        // Check for unusual decimal places (more than 2)
        if (amount.scale() > 2) {
            return ValidationResult.failure("Amount cannot have more than 2 decimal places");
        }
        
        log.debug("Amount validation passed");
        return ValidationResult.success("Amount is valid");
    }
    
    /**
     * Validates check date for reasonableness
     */
    public ValidationResult validateCheckDate(LocalDate checkDate) {
        log.debug("Validating check date: {}", checkDate);
        
        if (checkDate == null) {
            return ValidationResult.failure("Check date is required");
        }
        
        LocalDate today = LocalDate.now();
        LocalDate sixMonthsAgo = today.minusMonths(6);
        LocalDate oneMonthFuture = today.plusMonths(1);
        
        if (checkDate.isBefore(sixMonthsAgo)) {
            return ValidationResult.failure("Check is too old (older than 6 months)");
        }
        
        if (checkDate.isAfter(oneMonthFuture)) {
            return ValidationResult.failure("Check date is too far in the future");
        }
        
        // Weekend or holiday validation could be added here
        
        log.debug("Check date validation passed");
        return ValidationResult.success("Check date is valid");
    }
    
    /**
     * Validates image quality and security features
     */
    public ValidationResult validateImageQuality(BufferedImage image, byte[] imageData) {
        log.debug("Validating image quality");
        
        if (image == null || imageData == null) {
            return ValidationResult.failure("Image data is required");
        }
        
        // Check image dimensions
        if (image.getWidth() < MIN_IMAGE_WIDTH || image.getHeight() < MIN_IMAGE_HEIGHT) {
            return ValidationResult.failure(
                String.format("Image resolution too low: %dx%d (minimum: %dx%d)", 
                    image.getWidth(), image.getHeight(), MIN_IMAGE_WIDTH, MIN_IMAGE_HEIGHT)
            );
        }
        
        // Check file size (too small = poor quality, too large = processing issues)
        if (imageData.length < 10000) { // 10KB minimum
            return ValidationResult.failure("Image file size too small, likely poor quality");
        }
        
        if (imageData.length > 10_000_000) { // 10MB maximum
            return ValidationResult.failure("Image file size too large");
        }
        
        // Additional quality checks could be added here:
        // - Blur detection
        // - Contrast analysis
        // - Security feature detection
        // - Watermark detection
        
        log.debug("Image quality validation passed");
        return ValidationResult.success("Image quality is acceptable");
    }
    
    /**
     * Comprehensive fraud detection validation
     */
    public ValidationResult validateForFraud(String extractedText, BufferedImage frontImage, BufferedImage backImage) {
        log.debug("Performing fraud detection validation");
        
        List<String> suspiciousIndicators = new ArrayList<>();
        
        // Text-based fraud detection
        if (extractedText != null) {
            if (SUSPICIOUS_WORDS.matcher(extractedText).find()) {
                suspiciousIndicators.add("Suspicious words detected in check text");
            }
            
            // Check for unusual character patterns
            if (hasUnusualCharacterPatterns(extractedText)) {
                suspiciousIndicators.add("Unusual character patterns detected");
            }
        }
        
        // Image-based fraud detection
        if (frontImage != null) {
            if (hasDigitalAlterationSigns(frontImage)) {
                suspiciousIndicators.add("Possible digital alteration detected");
            }
        }
        
        // Cross-reference validation
        if (frontImage != null && backImage != null) {
            if (!areImagesConsistent(frontImage, backImage)) {
                suspiciousIndicators.add("Front and back images appear inconsistent");
            }
        }
        
        if (!suspiciousIndicators.isEmpty()) {
            String message = "Potential fraud indicators: " + String.join(", ", suspiciousIndicators);
            log.warn("Fraud validation failed: {}", message);
            return ValidationResult.failure(message);
        }
        
        log.debug("Fraud validation passed");
        return ValidationResult.success("No fraud indicators detected");
    }
    
    /**
     * Validates extracted data consistency
     */
    public ValidationResult validateDataConsistency(String routingNumber, String accountNumber, 
                                                  BigDecimal numericAmount, BigDecimal writtenAmount) {
        log.debug("Validating data consistency");
        
        List<String> inconsistencies = new ArrayList<>();
        
        // Compare numeric and written amounts
        if (numericAmount != null && writtenAmount != null) {
            BigDecimal difference = numericAmount.subtract(writtenAmount).abs();
            BigDecimal tolerance = numericAmount.multiply(new BigDecimal("0.01")); // 1% tolerance
            
            if (difference.compareTo(tolerance) > 0) {
                inconsistencies.add("Numeric and written amounts do not match");
            }
        }
        
        // Validate MICR line consistency
        if (routingNumber != null && accountNumber != null) {
            if (!isMICRLineConsistent(routingNumber, accountNumber)) {
                inconsistencies.add("MICR line data appears inconsistent");
            }
        }
        
        if (!inconsistencies.isEmpty()) {
            String message = "Data consistency issues: " + String.join(", ", inconsistencies);
            log.warn("Consistency validation failed: {}", message);
            return ValidationResult.failure(message);
        }
        
        log.debug("Data consistency validation passed");
        return ValidationResult.success("All extracted data is consistent");
    }
    
    // Helper methods
    
    private boolean isValidABACheckDigit(String routingNumber) {
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        int sum = 0;
        
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(routingNumber.charAt(i)) * weights[i];
        }
        
        return sum % 10 == 0;
    }
    
    private boolean isKnownFraudulentRoutingNumber(String routingNumber) {
        // Use comprehensive fraud detection service for real fraud validation
        return fraudDetectionService.isKnownFraudulentRoutingNumber(routingNumber);
    }
    
    private boolean isSuspiciousAccountNumber(String accountNumber) {
        // Check for all same digits
        if (accountNumber.matches("^(.)\\1+$")) {
            return true;
        }
        
        // Check for sequential digits
        if (isSequentialDigits(accountNumber)) {
            return true;
        }
        
        // Check for common test patterns
        String[] testPatterns = {"1234", "0000", "9999", "1111", "2222"};
        for (String pattern : testPatterns) {
            if (accountNumber.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isSequentialDigits(String number) {
        for (int i = 1; i < number.length(); i++) {
            int current = Character.getNumericValue(number.charAt(i));
            int previous = Character.getNumericValue(number.charAt(i - 1));
            if (current != previous + 1) {
                return false;
            }
        }
        return true;
    }
    
    private boolean hasUnusualCharacterPatterns(String text) {
        // Check for too many special characters
        long specialCharCount = text.chars()
            .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
            .count();
        
        if (specialCharCount > text.length() * 0.1) { // More than 10% special chars
            return true;
        }
        
        // Check for unusual repetition patterns
        if (text.matches(".*(.{3,})\\1{2,}.*")) { // 3+ char sequence repeated 3+ times
            return true;
        }
        
        return false;
    }
    
    private boolean hasDigitalAlterationSigns(BufferedImage image) {
        // Use comprehensive fraud detection service for real image analysis
        return fraudDetectionService.hasDigitalAlterationSigns(image);
    }
    
    private boolean areImagesConsistent(BufferedImage frontImage, BufferedImage backImage) {
        if (frontImage == null || backImage == null) {
            return false;
        }
        
        double aspectRatioFront = (double) frontImage.getWidth() / frontImage.getHeight();
        double aspectRatioBack = (double) backImage.getWidth() / backImage.getHeight();
        double aspectRatioDiff = Math.abs(aspectRatioFront - aspectRatioBack);
        
        if (aspectRatioDiff > 0.1) {
            log.warn("Image aspect ratios inconsistent: front={}, back={}", aspectRatioFront, aspectRatioBack);
            return false;
        }
        
        int sizeDiffWidth = Math.abs(frontImage.getWidth() - backImage.getWidth());
        int sizeDiffHeight = Math.abs(frontImage.getHeight() - backImage.getHeight());
        
        if (sizeDiffWidth > 100 || sizeDiffHeight > 100) {
            log.warn("Image dimensions significantly different: {}x{} vs {}x{}", 
                frontImage.getWidth(), frontImage.getHeight(), backImage.getWidth(), backImage.getHeight());
            return false;
        }
        
        return true;
    }
    
    private boolean isMICRLineConsistent(String routingNumber, String accountNumber) {
        if (routingNumber == null || accountNumber == null) {
            return false;
        }
        
        String cleanedRouting = routingNumber.replaceAll("[^0-9]", "");
        String cleanedAccount = accountNumber.replaceAll("[^0-9]", "");
        
        if (cleanedRouting.length() != 9) {
            return false;
        }
        
        if (cleanedAccount.length() < 4 || cleanedAccount.length() > 17) {
            return false;
        }
        
        if (!isValidABACheckDigit(cleanedRouting)) {
            log.warn("MICR line routing number check digit invalid");
            return false;
        }
        
        return true;
    }
    
    private String maskRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.length() < 4) {
            return "****";
        }
        return "****" + routingNumber.substring(routingNumber.length() - 4);
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final List<String> warnings;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
            this.warnings = new ArrayList<>();
        }
        
        public static ValidationResult success(String message) {
            return new ValidationResult(true, message);
        }
        
        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public ValidationResult addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }
    }
}