package com.waqiti.common.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CRITICAL PRODUCTION: Production Data Validation Service
 * PRODUCTION-READY: Comprehensive input validation and sanitization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionDataValidator {

    private final Validator validator;

    @Value("${waqiti.validation.strict-mode:true}")
    private boolean strictMode;

    @Value("${waqiti.validation.max-string-length:1000}")
    private int maxStringLength;

    @Value("${waqiti.validation.max-amount:1000000}")
    private BigDecimal maxTransactionAmount;

    // Security patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(union|select|insert|update|delete|drop|create|alter|exec|script|javascript|<script).*"
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i).*(script|javascript|vbscript|onload|onerror|onclick|alert|document\\.|window\\.).*"
    );
    
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    // Test data patterns that should never appear in production
    private static final Set<String> FORBIDDEN_TEST_PATTERNS = Set.of(
        "test@test.com",
        "fraud@example.com", 
        "example.com",
        "test.com",
        "localhost",
        "127.0.0.1",
        "192.168.",
        "10.0.0.",
        "TEST_",
        "EXAMPLE_",
        "DEMO_",
        "SAMPLE_"
    );

    @PostConstruct
    public void initializeValidator() {
        log.info("VALIDATION: Initializing production data validator - Strict mode: {}", strictMode);
        
        if (strictMode) {
            log.info("VALIDATION: Running in STRICT mode - All validation failures will be rejected");
        } else {
            log.warn("VALIDATION: Running in LENIENT mode - Consider enabling strict mode for production");
        }
    }

    /**
     * CRITICAL: Validate and sanitize any input data
     */
    public <T> ValidationResult<T> validate(T object) {
        if (object == null) {
            return ValidationResult.failure("Input object cannot be null");
        }

        List<String> violations = new ArrayList<>();
        
        try {
            // Bean validation
            Set<ConstraintViolation<T>> beanViolations = validator.validate(object);
            for (ConstraintViolation<T> violation : beanViolations) {
                violations.add(violation.getPropertyPath() + ": " + violation.getMessage());
            }

            // Custom security validation
            if (object instanceof String) {
                String stringValue = (String) object;
                ValidationResult<String> stringResult = validateString(stringValue);
                if (!stringResult.isValid()) {
                    violations.addAll(stringResult.getErrors());
                }
            }

            // Financial data validation
            if (object instanceof BigDecimal) {
                ValidationResult<BigDecimal> amountResult = validateAmount((BigDecimal) object);
                if (!amountResult.isValid()) {
                    violations.addAll(amountResult.getErrors());
                }
            }

            // Audit all validation attempts
            auditValidation(object, violations);

            if (violations.isEmpty()) {
                return ValidationResult.success(object);
            } else {
                return ValidationResult.failure(violations);
            }

        } catch (Exception e) {
            log.error("VALIDATION: Unexpected error during validation", e);
            return ValidationResult.failure("Validation system error");
        }
    }

    /**
     * CRITICAL: Validate string input for security threats
     */
    public ValidationResult<String> validateString(String input) {
        List<String> violations = new ArrayList<>();

        if (input == null) {
            return ValidationResult.valid(input); // Null is handled by bean validation
        }

        // Length validation
        if (input.length() > maxStringLength) {
            violations.add("String exceeds maximum length: " + maxStringLength);
        }

        // SQL injection detection
        if (SQL_INJECTION_PATTERN.matcher(input).matches()) {
            violations.add("Potential SQL injection detected");
            log.error("SECURITY: SQL injection attempt blocked: {}", sanitizeForLog(input));
        }

        // XSS detection
        if (XSS_PATTERN.matcher(input).matches()) {
            violations.add("Potential XSS attack detected");
            log.error("SECURITY: XSS attempt blocked: {}", sanitizeForLog(input));
        }

        // Test data detection
        for (String testPattern : FORBIDDEN_TEST_PATTERNS) {
            if (input.toLowerCase().contains(testPattern.toLowerCase())) {
                violations.add("Test data pattern detected in production: " + testPattern);
                log.error("PRODUCTION: Test data found: {}", sanitizeForLog(input));
            }
        }

        // Sensitive data detection
        if (CARD_NUMBER_PATTERN.matcher(input).find()) {
            violations.add("Potential card number detected - should be tokenized");
            log.error("SECURITY: Potential PAN detected in input");
        }

        if (SSN_PATTERN.matcher(input).find()) {
            violations.add("Potential SSN detected - should be encrypted");
            log.error("SECURITY: Potential SSN detected in input");
        }

        return violations.isEmpty() 
            ? ValidationResult.valid(input)
            : ValidationResult.invalid(violations);
    }

    /**
     * CRITICAL: Validate financial amounts
     */
    public ValidationResult<BigDecimal> validateAmount(BigDecimal amount) {
        List<String> violations = new ArrayList<>();

        if (amount == null) {
            violations.add("Amount cannot be null");
            return ValidationResult.invalid(violations);
        }

        // Range validation
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            violations.add("Amount cannot be negative");
        }

        if (amount.compareTo(maxTransactionAmount) > 0) {
            violations.add("Amount exceeds maximum allowed: " + maxTransactionAmount);
        }

        // Precision validation (max 2 decimal places for currency)
        if (amount.scale() > 2) {
            violations.add("Amount cannot have more than 2 decimal places");
        }

        // Suspicious amount patterns
        if (isSuspiciousAmount(amount)) {
            violations.add("Suspicious amount pattern detected");
            log.warn("FRAUD: Suspicious amount: {}", amount);
        }

        return violations.isEmpty()
            ? ValidationResult.valid(amount)
            : ValidationResult.invalid(violations);
    }

    /**
     * CRITICAL: Validate email address
     */
    public ValidationResult<String> validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.failure("Email cannot be empty");
        }

        String trimmed = email.trim().toLowerCase();
        List<String> violations = new ArrayList<>();

        // Format validation
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            violations.add("Invalid email format");
        }

        // Security validation
        ValidationResult<String> stringResult = validateString(trimmed);
        if (!stringResult.isValid()) {
            violations.addAll(stringResult.getErrors());
        }

        // Domain validation
        if (isDisposableEmailDomain(trimmed)) {
            violations.add("Disposable email addresses not allowed");
        }

        if (violations.isEmpty()) {
            return ValidationResult.success(trimmed);
        } else {
            return ValidationResult.failure(violations);
        }
    }

    /**
     * CRITICAL: Sanitize input for safe logging
     */
    public String sanitizeForLog(String input) {
        if (input == null) return "null";
        
        // Remove potential card numbers
        String sanitized = CARD_NUMBER_PATTERN.matcher(input).replaceAll("****-****-****-****");
        
        // Remove potential SSNs
        sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("***-**-****");
        
        // Truncate long strings
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100) + "...[TRUNCATED]";
        }
        
        return sanitized;
    }

    /**
     * CRITICAL: Validate customer ID format
     */
    public ValidationResult<String> validateCustomerId(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            return ValidationResult.failure("Customer ID cannot be empty");
        }

        String trimmed = customerId.trim();
        List<String> violations = new ArrayList<>();

        // Format validation - UUIDs or alphanumeric
        if (!Pattern.matches("^[a-fA-F0-9-]{36}$|^[a-zA-Z0-9_-]{6,50}$", trimmed)) {
            violations.add("Invalid customer ID format");
        }

        // Security validation
        ValidationResult<String> stringResult = validateString(trimmed);
        if (!stringResult.isValid()) {
            violations.addAll(stringResult.getErrors());
        }

        if (violations.isEmpty()) {
            return ValidationResult.success(trimmed);
        } else {
            return ValidationResult.failure(violations);
        }
    }

    /**
     * Check for suspicious amount patterns
     */
    private boolean isSuspiciousAmount(BigDecimal amount) {
        // Round amounts might be suspicious
        BigDecimal rounded = amount.setScale(0, RoundingMode.DOWN);
        if (amount.equals(rounded) && amount.compareTo(new BigDecimal("1000")) >= 0) {
            return true;
        }

        // Amounts just under reporting thresholds
        BigDecimal threshold = new BigDecimal("9999");
        return amount.compareTo(threshold) >= 0 && amount.compareTo(new BigDecimal("10000")) < 0;
    }

    /**
     * Check for disposable email domains
     */
    private boolean isDisposableEmailDomain(String email) {
        Set<String> disposableDomains = Set.of(
            "10minutemail.com", "tempmail.org", "guerrillamail.com",
            "mailinator.com", "throwaway.email"
        );

        String domain = email.substring(email.lastIndexOf('@') + 1);
        return disposableDomains.contains(domain.toLowerCase());
    }

    /**
     * Audit all validation attempts
     */
    private <T> void auditValidation(T object, List<String> violations) {
        if (!violations.isEmpty()) {
            log.warn("VALIDATION: Failed validation for object type: {} - Violations: {}", 
                    object.getClass().getSimpleName(), violations.size());
            
            if (log.isDebugEnabled()) {
                violations.forEach(violation -> log.debug("VALIDATION: {}", violation));
            }
        }
    }

    /**
     * PRODUCTION-GRADE: Validation result container with comprehensive error tracking
     * 
     * Provides both success/failure and valid/invalid factory methods for flexibility.
     * Immutable by design for thread safety. Supports detailed error reporting with
     * context preservation for security auditing and debugging.
     */
    public static class ValidationResult<T> {
        private final boolean valid;
        private final T data;
        private final List<String> errors;
        private final String validationContext;
        private final long validationTimestamp;

        private ValidationResult(boolean valid, T data, List<String> errors, String context) {
            this.valid = valid;
            this.data = data;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.validationContext = context;
            this.validationTimestamp = System.currentTimeMillis();
        }

        /**
         * PRODUCTION: Create successful validation result with validated data
         * @param data The validated and sanitized data
         * @return ValidationResult containing the valid data
         */
        public static <T> ValidationResult<T> success(T data) {
            if (data == null) {
                throw new IllegalArgumentException("Valid data cannot be null. Use failure() for invalid cases.");
            }
            return new ValidationResult<>(true, data, null, "success");
        }

        /**
         * PRODUCTION: Create valid result (alias for success for API compatibility)
         * @param data The validated and sanitized data
         * @return ValidationResult containing the valid data
         */
        public static <T> ValidationResult<T> valid(T data) {
            return success(data);
        }

        /**
         * PRODUCTION: Create failed validation result with single error
         * @param error Descriptive error message (will be logged for security audit)
         * @return ValidationResult containing the error
         */
        public static <T> ValidationResult<T> failure(String error) {
            if (error == null || error.trim().isEmpty()) {
                error = "Validation failed with unspecified error";
            }
            return new ValidationResult<>(false, null, List.of(error), "failure");
        }

        /**
         * PRODUCTION: Create invalid result with single error (alias for failure)
         * @param error Descriptive error message
         * @return ValidationResult containing the error
         */
        public static <T> ValidationResult<T> invalid(String error) {
            return failure(error);
        }

        /**
         * PRODUCTION: Create failed validation result with multiple errors
         * @param errors List of validation errors (will be deduplicated and sorted)
         * @return ValidationResult containing all errors
         */
        public static <T> ValidationResult<T> failure(List<String> errors) {
            if (errors == null || errors.isEmpty()) {
                errors = List.of("Validation failed with no specific errors provided");
            }
            
            // QUALITY: Deduplicate and sort errors for consistent reporting
            List<String> processedErrors = errors.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
                
            if (processedErrors.isEmpty()) {
                processedErrors = List.of("Validation failed");
            }
            
            return new ValidationResult<>(false, null, processedErrors, "failure:multiple");
        }

        /**
         * PRODUCTION: Create invalid result with multiple errors (alias for failure)
         * @param errors List of validation errors
         * @return ValidationResult containing all errors
         */
        public static <T> ValidationResult<T> invalid(List<String> errors) {
            return failure(errors);
        }

        /**
         * Check if validation passed
         * @return true if data is valid, false otherwise
         */
        public boolean isValid() { 
            return valid; 
        }

        /**
         * Check if validation failed (inverse of isValid for readability)
         * @return true if data is invalid, false otherwise
         */
        public boolean isInvalid() {
            return !valid;
        }

        /**
         * Get validated data (may be null if validation failed)
         * @return The validated data or null if invalid
         */
        public T getData() { 
            return data; 
        }

        /**
         * Get list of validation errors (empty if valid)
         * @return Immutable copy of error list
         */
        public List<String> getErrors() { 
            return new ArrayList<>(errors); 
        }

        /**
         * Get formatted error message for display/logging
         * @return Semicolon-separated error messages
         */
        public String getErrorMessage() { 
            if (errors.isEmpty()) {
                return "No errors";
            }
            return String.join("; ", errors); 
        }

        /**
         * Get validation context for debugging
         * @return Context string indicating how this result was created
         */
        public String getValidationContext() {
            return validationContext;
        }

        /**
         * Get validation timestamp for audit trails
         * @return Epoch milliseconds when validation occurred
         */
        public long getValidationTimestamp() {
            return validationTimestamp;
        }

        /**
         * PRODUCTION: Get data or throw exception if invalid
         * Use this when validation failure should halt processing
         * 
         * @return The validated data
         * @throws ValidationException if validation failed
         */
        public T getDataOrThrow() {
            if (!valid) {
                throw new IllegalArgumentException(
                    String.format("Validation failed [context: %s]: %s", 
                        validationContext, getErrorMessage())
                );
            }
            return data;
        }

        /**
         * PRODUCTION: Get data or return default value if invalid
         * Use this for graceful degradation scenarios
         * 
         * @param defaultValue Value to return if validation failed
         * @return The validated data or default value
         */
        public T getDataOrDefault(T defaultValue) {
            return valid ? data : defaultValue;
        }

        /**
         * PRODUCTION: Get first error message (for simple error display)
         * @return First error message or null if valid
         */
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        /**
         * PRODUCTION: Get count of validation errors
         * @return Number of errors (0 if valid)
         */
        public int getErrorCount() {
            return errors.size();
        }

        /**
         * PRODUCTION: Check if validation has specific error
         * @param errorText Text to search for in error messages
         * @return true if any error contains the text
         */
        public boolean hasError(String errorText) {
            if (errorText == null) {
                return false;
            }
            return errors.stream()
                .anyMatch(e -> e.toLowerCase().contains(errorText.toLowerCase()));
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, errors=%d, context=%s}", 
                valid, errors.size(), validationContext);
        }
    }

    /**
     * Validation exception
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}