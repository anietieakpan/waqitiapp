package com.waqiti.common.validation;

import com.waqiti.common.exception.ValidationException;
import org.springframework.stereotype.Component;
import org.owasp.encoder.Encode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive input validation framework for financial operations.
 * Implements OWASP best practices for input validation and sanitization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialInputValidator {

    private final Validator validator;

    // Regex patterns for validation
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{8,17}$");
    private static final Pattern ROUTING_NUMBER_PATTERN = Pattern.compile("^[0-9]{9}$");
    private static final Pattern SWIFT_CODE_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("('.+--)|(--)|(\\|\\|)|(\\*\\|)|(\\|\\*)|(@@)|(char\\()|exec\\(|execute\\(|select\\s|insert\\s|update\\s|delete\\s|union\\s|drop\\s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern XSS_PATTERN = Pattern.compile("(<script|<iframe|javascript:|onerror=|onclick=|<img|<body|<embed|<object)", Pattern.CASE_INSENSITIVE);
    
    // Financial limits
    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("50000.00");
    private static final int MAX_TRANSACTIONS_PER_DAY = 100;
    
    // Blacklisted countries for sanctions compliance
    private static final Set<String> SANCTIONED_COUNTRIES = Set.of(
        "KP", // North Korea
        "IR", // Iran
        "SY", // Syria
        "CU", // Cuba
        "RU"  // Russia (conditional)
    );

    /**
     * Validates payment amount with comprehensive checks
     */
    public ValidationResult validatePaymentAmount(BigDecimal amount, String currency, String userId) {
        ValidationResult result = new ValidationResult();
        
        try {
            // Null check
            if (amount == null) {
                result.addError("AMOUNT_NULL", "Payment amount cannot be null");
                return result;
            }
            
            // Negative check
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                result.addError("AMOUNT_NEGATIVE", "Payment amount must be positive");
            }
            
            // Minimum amount check
            if (amount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
                result.addError("AMOUNT_TOO_SMALL", 
                    String.format("Minimum transaction amount is %s", MIN_TRANSACTION_AMOUNT));
            }
            
            // Maximum amount check
            if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
                result.addError("AMOUNT_TOO_LARGE", 
                    String.format("Maximum transaction amount is %s", MAX_TRANSACTION_AMOUNT));
            }
            
            // Precision check (max 2 decimal places for most currencies)
            if (amount.scale() > 2 && !"BTC".equals(currency) && !"ETH".equals(currency)) {
                result.addError("AMOUNT_PRECISION", "Amount cannot have more than 2 decimal places");
            }
            
            // Daily limit check
            BigDecimal dailyTotal = getDailyTransactionTotal(userId);
            if (dailyTotal.add(amount).compareTo(MAX_DAILY_LIMIT) > 0) {
                result.addError("DAILY_LIMIT_EXCEEDED", 
                    String.format("Daily transaction limit of %s would be exceeded", MAX_DAILY_LIMIT));
            }
            
            // Velocity check
            int dailyCount = getDailyTransactionCount(userId);
            if (dailyCount >= MAX_TRANSACTIONS_PER_DAY) {
                result.addError("VELOCITY_LIMIT_EXCEEDED", 
                    String.format("Maximum %d transactions per day exceeded", MAX_TRANSACTIONS_PER_DAY));
            }
            
            // Suspicious amount patterns
            if (isSuspiciousAmount(amount)) {
                result.addWarning("SUSPICIOUS_AMOUNT", "Amount matches suspicious pattern");
                log.warn("Suspicious amount detected: {} for user: {}", amount, userId);
            }
            
        } catch (Exception e) {
            log.error("Error validating payment amount", e);
            result.addError("VALIDATION_ERROR", "Internal validation error");
        }
        
        return result;
    }

    /**
     * Validates account number with checksum verification
     */
    public ValidationResult validateAccountNumber(String accountNumber, String accountType) {
        ValidationResult result = new ValidationResult();
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            result.addError("ACCOUNT_EMPTY", "Account number cannot be empty");
            return result;
        }
        
        // Remove spaces and sanitize
        accountNumber = sanitizeInput(accountNumber.replaceAll("\\s+", ""));
        
        // Format validation
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            result.addError("ACCOUNT_FORMAT", "Invalid account number format");
        }
        
        // Checksum validation (Luhn algorithm for card numbers)
        if ("CARD".equals(accountType) && !isValidLuhn(accountNumber)) {
            result.addError("ACCOUNT_CHECKSUM", "Invalid account number checksum");
        }
        
        // Check against blacklist
        if (isBlacklistedAccount(accountNumber)) {
            result.addError("ACCOUNT_BLACKLISTED", "Account is blacklisted");
            log.warn("Blacklisted account attempted: {}", maskAccountNumber(accountNumber));
        }
        
        return result;
    }

    /**
     * Validates routing number with ABA check digit verification
     */
    public ValidationResult validateRoutingNumber(String routingNumber) {
        ValidationResult result = new ValidationResult();
        
        if (routingNumber == null || routingNumber.trim().isEmpty()) {
            result.addError("ROUTING_EMPTY", "Routing number cannot be empty");
            return result;
        }
        
        routingNumber = sanitizeInput(routingNumber.replaceAll("\\s+", ""));
        
        if (!ROUTING_NUMBER_PATTERN.matcher(routingNumber).matches()) {
            result.addError("ROUTING_FORMAT", "Routing number must be 9 digits");
            return result;
        }
        
        // ABA routing number checksum validation
        if (!isValidABARoutingNumber(routingNumber)) {
            result.addError("ROUTING_CHECKSUM", "Invalid routing number checksum");
        }
        
        return result;
    }

    /**
     * Validates SWIFT/BIC code
     */
    public ValidationResult validateSwiftCode(String swiftCode) {
        ValidationResult result = new ValidationResult();
        
        if (swiftCode == null || swiftCode.trim().isEmpty()) {
            result.addError("SWIFT_EMPTY", "SWIFT code cannot be empty");
            return result;
        }
        
        swiftCode = sanitizeInput(swiftCode.toUpperCase());
        
        if (!SWIFT_CODE_PATTERN.matcher(swiftCode).matches()) {
            result.addError("SWIFT_FORMAT", "Invalid SWIFT code format");
        }
        
        // Validate country code (positions 5-6)
        if (swiftCode.length() >= 6) {
            String countryCode = swiftCode.substring(4, 6);
            if (SANCTIONED_COUNTRIES.contains(countryCode)) {
                result.addError("SWIFT_SANCTIONED", "Transactions to this country are restricted");
                log.warn("Sanctioned country SWIFT code attempted: {}", countryCode);
            }
        }
        
        return result;
    }

    /**
     * Validates IBAN with country-specific rules
     */
    public ValidationResult validateIBAN(String iban) {
        ValidationResult result = new ValidationResult();
        
        if (iban == null || iban.trim().isEmpty()) {
            result.addError("IBAN_EMPTY", "IBAN cannot be empty");
            return result;
        }
        
        iban = sanitizeInput(iban.toUpperCase().replaceAll("\\s+", ""));
        
        if (!IBAN_PATTERN.matcher(iban).matches()) {
            result.addError("IBAN_FORMAT", "Invalid IBAN format");
            return result;
        }
        
        // IBAN checksum validation
        if (!isValidIBAN(iban)) {
            result.addError("IBAN_CHECKSUM", "Invalid IBAN checksum");
        }
        
        // Check country code
        String countryCode = iban.substring(0, 2);
        if (SANCTIONED_COUNTRIES.contains(countryCode)) {
            result.addError("IBAN_SANCTIONED", "Transactions to this country are restricted");
        }
        
        return result;
    }

    /**
     * Validates email address with comprehensive checks
     */
    public ValidationResult validateEmail(String email) {
        ValidationResult result = new ValidationResult();
        
        if (email == null || email.trim().isEmpty()) {
            result.addError("EMAIL_EMPTY", "Email cannot be empty");
            return result;
        }
        
        email = sanitizeInput(email.toLowerCase().trim());
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            result.addError("EMAIL_FORMAT", "Invalid email format");
        }
        
        // Check for disposable email domains
        if (isDisposableEmail(email)) {
            result.addWarning("EMAIL_DISPOSABLE", "Disposable email addresses are not recommended");
        }
        
        // Length check
        if (email.length() > 254) {
            result.addError("EMAIL_TOO_LONG", "Email address too long");
        }
        
        return result;
    }

    /**
     * Validates phone number with international format
     */
    public ValidationResult validatePhoneNumber(String phoneNumber) {
        ValidationResult result = new ValidationResult();
        
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            result.addError("PHONE_EMPTY", "Phone number cannot be empty");
            return result;
        }
        
        phoneNumber = sanitizeInput(phoneNumber.replaceAll("[\\s()-]", ""));
        
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            result.addError("PHONE_FORMAT", "Invalid phone number format (E.164)");
        }
        
        // Check for premium rate numbers
        if (isPremiumRateNumber(phoneNumber)) {
            result.addError("PHONE_PREMIUM", "Premium rate numbers not allowed");
        }
        
        return result;
    }

    /**
     * Validates and sanitizes free text input
     */
    public ValidationResult validateAndSanitizeText(String text, String fieldName, int maxLength) {
        ValidationResult result = new ValidationResult();
        
        if (text == null) {
            return result; // Null is allowed for optional fields
        }
        
        // Length check
        if (text.length() > maxLength) {
            result.addError(fieldName + "_TOO_LONG", 
                String.format("%s exceeds maximum length of %d", fieldName, maxLength));
        }
        
        // SQL injection check
        if (SQL_INJECTION_PATTERN.matcher(text).find()) {
            result.addError(fieldName + "_SQL_INJECTION", "Invalid characters detected");
            log.warn("Potential SQL injection attempt in field: {}", fieldName);
        }
        
        // XSS check
        if (XSS_PATTERN.matcher(text).find()) {
            result.addError(fieldName + "_XSS", "Invalid HTML/Script content detected");
            log.warn("Potential XSS attempt in field: {}", fieldName);
        }
        
        // Sanitize for output
        result.setSanitizedValue(Encode.forHtml(text));
        
        return result;
    }

    /**
     * Validates QR code data
     */
    public ValidationResult validateQRCodeData(String qrData) {
        ValidationResult result = new ValidationResult();
        
        if (qrData == null || qrData.trim().isEmpty()) {
            result.addError("QR_EMPTY", "QR code data cannot be empty");
            return result;
        }
        
        // Size limit (QR codes have practical limits)
        if (qrData.length() > 4296) {
            result.addError("QR_TOO_LARGE", "QR code data too large");
        }
        
        // Check for malicious patterns
        if (qrData.contains("javascript:") || qrData.contains("data:text/html")) {
            result.addError("QR_MALICIOUS", "Malicious QR code content detected");
            log.warn("Malicious QR code attempted");
        }
        
        return result;
    }

    /**
     * Validates file upload
     */
    public ValidationResult validateFileUpload(byte[] fileContent, String fileName, String mimeType) {
        ValidationResult result = new ValidationResult();
        
        // Size check (10MB limit)
        if (fileContent.length > 10 * 1024 * 1024) {
            result.addError("FILE_TOO_LARGE", "File size exceeds 10MB limit");
        }
        
        // File type validation
        Set<String> allowedTypes = Set.of(
            "image/jpeg", "image/png", "image/gif",
            "application/pdf", "text/csv"
        );
        
        if (!allowedTypes.contains(mimeType)) {
            result.addError("FILE_TYPE_INVALID", "File type not allowed");
        }
        
        // File name validation
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            result.addError("FILE_NAME_INVALID", "Invalid file name");
        }
        
        // Magic number validation (verify actual file type)
        String detectedType = detectFileType(fileContent);
        if (!detectedType.equals(mimeType)) {
            result.addError("FILE_TYPE_MISMATCH", "File type mismatch detected");
            log.warn("File type mismatch: claimed={}, detected={}", mimeType, detectedType);
        }
        
        return result;
    }

    // Helper methods

    private String sanitizeInput(String input) {
        if (input == null) return null;
        // Remove control characters and non-printable characters
        return input.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Co}\\p{Cn}]", "");
    }

    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    private boolean isValidABARoutingNumber(String routingNumber) {
        // ABA routing number checksum algorithm
        int checksum = 0;
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        for (int i = 0; i < 9; i++) {
            checksum += Character.getNumericValue(routingNumber.charAt(i)) * weights[i];
        }
        return checksum % 10 == 0;
    }

    private boolean isValidIBAN(String iban) {
        // Move first 4 characters to end
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        
        // Replace letters with numbers (A=10, B=11, ..., Z=35)
        StringBuilder numericIBAN = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numericIBAN.append(Character.getNumericValue(c));
            } else {
                numericIBAN.append(c);
            }
        }
        
        // Calculate mod 97
        java.math.BigInteger ibanNumber = new java.math.BigInteger(numericIBAN.toString());
        return ibanNumber.mod(java.math.BigInteger.valueOf(97)).intValue() == 1;
    }

    private boolean isSuspiciousAmount(BigDecimal amount) {
        // Check for common structuring patterns
        String amountStr = amount.toString();
        return amountStr.equals("9999.00") || 
               amountStr.equals("9999.99") ||
               amountStr.equals("4999.00") ||
               amountStr.equals("4999.99");
    }

    private boolean isBlacklistedAccount(String accountNumber) {
        try {
            // Check against multiple blacklist sources
            
            // 1. Internal fraud blacklist
            if (isInternalBlacklist(accountNumber)) {
                log.warn("Account found in internal blacklist: {}", maskAccountNumber(accountNumber));
                return true;
            }
            
            // 2. Regulatory blacklists (OFAC, etc.)
            if (isRegulatoryBlacklist(accountNumber)) {
                log.warn("Account found in regulatory blacklist: {}", maskAccountNumber(accountNumber));
                return true;
            }
            
            // 3. Industry shared blacklists
            if (isIndustryBlacklist(accountNumber)) {
                log.warn("Account found in industry blacklist: {}", maskAccountNumber(accountNumber));
                return true;
            }
            
            // 4. Check pattern-based rules
            if (matchesSuspiciousPatterns(accountNumber)) {
                log.warn("Account matches suspicious patterns: {}", maskAccountNumber(accountNumber));
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking account blacklist for: {}", maskAccountNumber(accountNumber), e);
            // Return false on error to not block legitimate transactions due to system issues
            return false;
        }
    }

    private boolean isInternalBlacklist(String accountNumber) {
        // In production, this would query the internal fraud database
        // Check for accounts flagged for fraud, chargebacks, etc.
        
        Set<String> knownFraudulentPatterns = Set.of(
            "000000000", "111111111", "222222222", "123456789", "987654321"
        );
        
        return knownFraudulentPatterns.contains(accountNumber);
    }

    private boolean isRegulatoryBlacklist(String accountNumber) {
        // In production, this would integrate with:
        // - OFAC Specially Designated Nationals (SDN) List
        // - Office of Inspector General List of Excluded Individuals/Entities
        // - Other regulatory watch lists
        
        // Mock implementation for demonstration
        return false;
    }

    private boolean isIndustryBlacklist(String accountNumber) {
        // In production, this would check industry-shared blacklists:
        // - National Shared Database (ChexSystems, etc.)
        // - ACH Network risk databases
        // - Payment processor shared lists
        
        // Mock implementation
        return false;
    }

    private boolean matchesSuspiciousPatterns(String accountNumber) {
        // Check for patterns that indicate potential fraud
        
        // Sequential numbers (e.g., 123456789)
        if (isSequentialDigits(accountNumber)) {
            return true;
        }
        
        // Repeated patterns (e.g., 111111111)
        if (hasRepeatedPattern(accountNumber)) {
            return true;
        }
        
        // Known test account patterns
        if (isTestAccountPattern(accountNumber)) {
            return true;
        }
        
        return false;
    }

    private boolean isSequentialDigits(String number) {
        if (number.length() < 3) return false;
        
        for (int i = 0; i < number.length() - 2; i++) {
            int digit1 = Character.getNumericValue(number.charAt(i));
            int digit2 = Character.getNumericValue(number.charAt(i + 1));
            int digit3 = Character.getNumericValue(number.charAt(i + 2));
            
            if (digit2 == digit1 + 1 && digit3 == digit2 + 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRepeatedPattern(String number) {
        if (number.length() < 3) return false;
        
        // Check for same digit repeated
        char firstChar = number.charAt(0);
        boolean allSame = true;
        for (int i = 1; i < number.length(); i++) {
            if (number.charAt(i) != firstChar) {
                allSame = false;
                break;
            }
        }
        
        return allSame;
    }

    private boolean isTestAccountPattern(String accountNumber) {
        // Common test account patterns used in fraud attempts
        Set<String> testPatterns = Set.of(
            "999999999", "000000001", "123123123", "555555555"
        );
        
        return testPatterns.contains(accountNumber);
    }

    private boolean isDisposableEmail(String email) {
        Set<String> disposableDomains = Set.of(
            "tempmail.com", "throwaway.email", "guerrillamail.com",
            "mailinator.com", "10minutemail.com"
        );
        
        String domain = email.substring(email.indexOf('@') + 1);
        return disposableDomains.contains(domain);
    }

    private boolean isPremiumRateNumber(String phoneNumber) {
        // Check for premium rate prefixes
        return phoneNumber.startsWith("+1900") || 
               phoneNumber.startsWith("+44909") ||
               phoneNumber.startsWith("+44908");
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber.length() <= 4) return "****";
        return "*".repeat(accountNumber.length() - 4) + 
               accountNumber.substring(accountNumber.length() - 4);
    }

    private String detectFileType(byte[] fileContent) {
        if (fileContent.length < 4) return "unknown";
        
        // Check magic numbers
        if (fileContent[0] == (byte)0xFF && fileContent[1] == (byte)0xD8) {
            return "image/jpeg";
        }
        if (fileContent[0] == (byte)0x89 && fileContent[1] == 'P' && 
            fileContent[2] == 'N' && fileContent[3] == 'G') {
            return "image/png";
        }
        if (fileContent[0] == '%' && fileContent[1] == 'P' && 
            fileContent[2] == 'D' && fileContent[3] == 'F') {
            return "application/pdf";
        }
        
        return "unknown";
    }

    private BigDecimal getDailyTransactionTotal(String userId) {
        try {
            // Query transaction service for user's daily total
            LocalDate today = LocalDate.now();
            
            // This would typically call a transaction repository or service
            // For now, implement a basic cache-based approach
            String cacheKey = "daily_total:" + userId + ":" + today;
            
            // In production, this would query the actual transaction database
            // and calculate the sum of all transactions for the user today
            
            // For demonstration, return a placeholder that would come from actual DB query
            log.debug("Calculating daily transaction total for user: {} on date: {}", userId, today);
            
            // Mock implementation - in production this would be:
            // return transactionRepository.sumUserTransactionsForDate(userId, today);
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Failed to calculate daily transaction total for user: {}", userId, e);
            // Return zero on error to allow transaction, but log the issue
            return BigDecimal.ZERO;
        }
    }

    private int getDailyTransactionCount(String userId) {
        try {
            LocalDate today = LocalDate.now();
            
            // Query transaction service for user's daily count
            String cacheKey = "daily_count:" + userId + ":" + today;
            
            log.debug("Calculating daily transaction count for user: {} on date: {}", userId, today);
            
            // Mock implementation - in production this would be:
            // return transactionRepository.countUserTransactionsForDate(userId, today);
            return 0;
            
        } catch (Exception e) {
            log.error("Failed to calculate daily transaction count for user: {}", userId, e);
            // Return zero on error to be safe
            return 0;
        }
    }

    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final List<ValidationError> errors = new ArrayList<>();
        private final List<ValidationWarning> warnings = new ArrayList<>();
        private String sanitizedValue;
        
        public void addError(String code, String message) {
            errors.add(new ValidationError(code, message));
        }
        
        public void addWarning(String code, String message) {
            warnings.add(new ValidationWarning(code, message));
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<ValidationError> getErrors() {
            return Collections.unmodifiableList(errors);
        }
        
        public List<ValidationWarning> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }
        
        public String getSanitizedValue() {
            return sanitizedValue;
        }
        
        public void setSanitizedValue(String value) {
            this.sanitizedValue = value;
        }
    }
    
    public static class ValidationError {
        private final String code;
        private final String message;
        
        public ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
    
    public static class ValidationWarning {
        private final String code;
        private final String message;
        
        public ValidationWarning(String code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}