package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.exception.GenericValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Advanced Input Validation Framework
 * Provides comprehensive validation and sanitization with threat detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedInputValidationFramework {

    @Autowired(required = false)
    private SqlInjectionValidator sqlInjectionValidator;
    
    @Autowired(required = false)
    private XSSProtectionService xssProtectionService;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${security.validation.strict-mode:true}")
    private boolean strictMode;
    
    @Value("${security.validation.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${security.validation.rate-limit.max-violations:10}")
    private int maxViolationsPerHour;
    
    @Value("${security.validation.anomaly-detection.enabled:true}")
    private boolean anomalyDetectionEnabled;
    
    // Cache for validation rules to improve performance
    private final Map<String, ValidationRule> ruleCache = new ConcurrentHashMap<>();
    
    // Comprehensive validation patterns
    private static final Map<ValidationType, Pattern> VALIDATION_PATTERNS = Map.of(
        ValidationType.EMAIL, Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
        ValidationType.PHONE, Pattern.compile("^[+]?[1-9]?[0-9]{7,15}$"),
        ValidationType.ALPHANUMERIC, Pattern.compile("^[a-zA-Z0-9]+$"),
        ValidationType.NUMERIC, Pattern.compile("^-?\\d+(\\.\\d+)?$"),
        ValidationType.UUID, Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
        ValidationType.ISO_DATE, Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"),
        ValidationType.SAFE_TEXT, Pattern.compile("^[a-zA-Z0-9\\s.,!?'-]+$"),
        ValidationType.USERNAME, Pattern.compile("^[a-zA-Z0-9_.-]{3,50}$"),
        ValidationType.PASSWORD_STRENGTH, Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")
    );

    /**
     * Comprehensive input validation with threat detection
     */
    public ValidationResult validateInput(String input, ValidationContext context) {
        if (input == null) {
            return ValidationResult.success(null);
        }

        try {
            // Rate limiting check
            if (rateLimitEnabled && isRateLimited(context)) {
                return ValidationResult.failure("Rate limit exceeded", ThreatLevel.HIGH);
            }

            // Multi-layer validation
            ValidationResult result = performMultiLayerValidation(input, context);
            
            // Record validation metrics
            recordValidationMetrics(context, result);
            
            // Anomaly detection
            if (anomalyDetectionEnabled && result.isValid()) {
                double anomalyScore = detectValidationAnomalies(input, context);
                if (anomalyScore > 70.0) {
                    result = ValidationResult.failure("Anomalous input pattern detected", ThreatLevel.MEDIUM);
                }
            }

            // Update threat intelligence
            if (!result.isValid()) {
                updateThreatIntelligence(input, context, result);
            }

            return result;

        } catch (Exception e) {
            log.error("Validation framework error for context: {}", context.getFieldName(), e);
            return ValidationResult.failure("Validation system error", ThreatLevel.HIGH);
        }
    }

    /**
     * Batch validation for multiple inputs
     */
    public Map<String, ValidationResult> validateBatch(Map<String, String> inputs, ValidationContext baseContext) {
        Map<String, ValidationResult> results = new HashMap<>();
        
        for (Map.Entry<String, String> entry : inputs.entrySet()) {
            ValidationContext context = baseContext.toBuilder()
                .fieldName(entry.getKey())
                .build();
            
            ValidationResult result = validateInput(entry.getValue(), context);
            results.put(entry.getKey(), result);
        }
        
        return results;
    }

    /**
     * Sanitize input based on validation rules
     */
    public String sanitizeInput(String input, ValidationContext context) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String sanitized = input;

        // Apply XSS protection if available
        if (xssProtectionService != null) {
            try {
                switch (context.getValidationType()) {
                    case EMAIL:
                        sanitized = xssProtectionService.sanitizeEmail(sanitized);
                        break;
                    case PHONE:
                        sanitized = xssProtectionService.sanitizePhoneNumber(sanitized);
                        break;
                    case URL:
                        sanitized = xssProtectionService.sanitizeURL(sanitized);
                        break;
                    case FILENAME:
                        sanitized = xssProtectionService.sanitizeFileName(sanitized);
                        break;
                    case RICH_TEXT:
                        sanitized = xssProtectionService.sanitizeRichText(sanitized);
                        break;
                    default:
                        sanitized = xssProtectionService.sanitizeInput(sanitized);
                }
            } catch (XSSProtectionService.XSSValidationException e) {
                log.warn("XSS detected during sanitization: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid input content");
            }
        }

        // Apply SQL injection protection if available
        if (sqlInjectionValidator != null && context.isSqlInjectionCheck()) {
            if (!sqlInjectionValidator.isInputSafe(sanitized, context.getFieldName())) {
                throw new IllegalArgumentException("SQL injection attempt detected");
            }
        }

        // Apply context-specific sanitization
        sanitized = applySanitizationRules(sanitized, context);

        return sanitized;
    }

    /**
     * Create validation rule for a field
     */
    public ValidationRule createValidationRule(String fieldName, ValidationType type) {
        return ValidationRule.builder()
            .fieldName(fieldName)
            .validationType(type)
            .required(false)
            .minLength(0)
            .maxLength(Integer.MAX_VALUE)
            .pattern(VALIDATION_PATTERNS.get(type))
            .allowedValues(Collections.emptySet())
            .build();
    }

    /**
     * Register custom validation rule
     */
    public void registerValidationRule(ValidationRule rule) {
        ruleCache.put(rule.getFieldName(), rule);
        log.debug("Registered validation rule for field: {}", rule.getFieldName());
    }

    /**
     * Validate financial amounts with precision
     */
    public ValidationResult validateFinancialAmount(String amount, String currency) {
        if (amount == null || amount.trim().isEmpty()) {
            return ValidationResult.failure("Amount is required", ThreatLevel.LOW);
        }

        try {
            BigDecimal value = new BigDecimal(amount);
            
            // Check for reasonable bounds
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return ValidationResult.failure("Negative amounts not allowed", ThreatLevel.MEDIUM);
            }
            
            if (value.compareTo(new BigDecimal("1000000000")) > 0) {
                return ValidationResult.failure("Amount exceeds maximum limit", ThreatLevel.HIGH);
            }
            
            // Check decimal precision
            if (value.scale() > 2) {
                return ValidationResult.failure("Invalid decimal precision", ThreatLevel.LOW);
            }
            
            // Currency-specific validation
            if (currency != null && !isValidCurrency(currency)) {
                return ValidationResult.failure("Invalid currency code", ThreatLevel.MEDIUM);
            }
            
            return ValidationResult.success(value.toString());
            
        } catch (NumberFormatException e) {
            return ValidationResult.failure("Invalid number format", ThreatLevel.MEDIUM);
        }
    }

    /**
     * Validate date inputs with range checking
     */
    public ValidationResult validateDate(String dateString, DateValidationType dateType) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return ValidationResult.success(null);
        }

        try {
            LocalDate date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate now = LocalDate.now();
            
            switch (dateType) {
                case BIRTH_DATE:
                    if (date.isAfter(now)) {
                        return ValidationResult.failure("Birth date cannot be in the future", ThreatLevel.MEDIUM);
                    }
                    if (date.isBefore(now.minusYears(150))) {
                        return ValidationResult.failure("Invalid birth date", ThreatLevel.MEDIUM);
                    }
                    break;
                    
                case FUTURE_DATE:
                    if (date.isBefore(now)) {
                        return ValidationResult.failure("Date must be in the future", ThreatLevel.LOW);
                    }
                    break;
                    
                case PAST_DATE:
                    if (date.isAfter(now)) {
                        return ValidationResult.failure("Date must be in the past", ThreatLevel.LOW);
                    }
                    break;
                    
                case RECENT_DATE:
                    if (date.isBefore(now.minusDays(30)) || date.isAfter(now.plusDays(30))) {
                        return ValidationResult.failure("Date must be within 30 days", ThreatLevel.MEDIUM);
                    }
                    break;
            }
            
            return ValidationResult.success(dateString);
            
        } catch (DateTimeParseException e) {
            return ValidationResult.failure("Invalid date format", ThreatLevel.MEDIUM);
        }
    }

    /**
     * Advanced password validation
     */
    public ValidationResult validatePassword(String password, String username) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.failure("Password is required", ThreatLevel.LOW);
        }

        List<String> violations = new ArrayList<>();
        
        // Length check
        if (password.length() < 8) {
            violations.add("Password must be at least 8 characters");
        }
        
        if (password.length() > 128) {
            violations.add("Password must not exceed 128 characters");
        }
        
        // Complexity checks
        if (!password.matches(".*[a-z].*")) {
            violations.add("Password must contain lowercase letters");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            violations.add("Password must contain uppercase letters");
        }
        
        if (!password.matches(".*\\d.*")) {
            violations.add("Password must contain numbers");
        }
        
        if (!password.matches(".*[@$!%*?&].*")) {
            violations.add("Password must contain special characters");
        }
        
        // Common password checks
        if (isCommonPassword(password)) {
            violations.add("Password is too common");
        }
        
        // Username similarity check
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            violations.add("Password cannot contain username");
        }
        
        // Sequential character check
        if (containsSequentialCharacters(password)) {
            violations.add("Password cannot contain sequential characters");
        }
        
        if (!violations.isEmpty()) {
            return ValidationResult.failure(String.join(", ", violations), ThreatLevel.MEDIUM);
        }
        
        return ValidationResult.success("Password is valid");
    }

    /**
     * Validate API keys and tokens
     */
    public ValidationResult validateApiKey(String apiKey, ApiKeyType keyType) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ValidationResult.failure("API key is required", ThreatLevel.HIGH);
        }

        // Check format based on key type
        switch (keyType) {
            case JWT:
                if (!apiKey.matches("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$")) {
                    return ValidationResult.failure("Invalid JWT format", ThreatLevel.HIGH);
                }
                break;
                
            case UUID_BASED:
                if (!VALIDATION_PATTERNS.get(ValidationType.UUID).matcher(apiKey).matches()) {
                    return ValidationResult.failure("Invalid UUID format", ThreatLevel.HIGH);
                }
                break;
                
            case CUSTOM:
                if (apiKey.length() < 32 || apiKey.length() > 128) {
                    return ValidationResult.failure("Invalid API key length", ThreatLevel.HIGH);
                }
                break;
        }

        // Check for suspicious patterns
        if (containsSuspiciousPatterns(apiKey)) {
            return ValidationResult.failure("API key contains suspicious patterns", ThreatLevel.HIGH);
        }

        return ValidationResult.success(apiKey);
    }

    // Private helper methods

    private ValidationResult performMultiLayerValidation(String input, ValidationContext context) {
        // Layer 1: Basic format validation
        ValidationResult formatResult = validateFormat(input, context);
        if (!formatResult.isValid()) {
            return formatResult;
        }

        // Layer 2: Length validation
        ValidationResult lengthResult = validateLength(input, context);
        if (!lengthResult.isValid()) {
            return lengthResult;
        }

        // Layer 3: Pattern validation
        ValidationResult patternResult = validatePattern(input, context);
        if (!patternResult.isValid()) {
            return patternResult;
        }

        // Layer 4: Content validation
        ValidationResult contentResult = validateContent(input, context);
        if (!contentResult.isValid()) {
            return contentResult;
        }

        // Layer 5: Business logic validation
        return validateBusinessLogic(input, context);
    }

    private ValidationResult validateFormat(String input, ValidationContext context) {
        if (context.isRequired() && (input == null || input.trim().isEmpty())) {
            return ValidationResult.failure("Field is required", ThreatLevel.LOW);
        }

        return ValidationResult.success(input);
    }

    private ValidationResult validateLength(String input, ValidationContext context) {
        if (input == null) return ValidationResult.success(null);

        if (input.length() < context.getMinLength()) {
            return ValidationResult.failure(
                String.format("Input too short (min: %d)", context.getMinLength()), 
                ThreatLevel.LOW);
        }

        if (input.length() > context.getMaxLength()) {
            return ValidationResult.failure(
                String.format("Input too long (max: %d)", context.getMaxLength()), 
                ThreatLevel.MEDIUM);
        }

        return ValidationResult.success(input);
    }

    private ValidationResult validatePattern(String input, ValidationContext context) {
        if (input == null || context.getPattern() == null) {
            return ValidationResult.success(input);
        }

        if (!context.getPattern().matcher(input).matches()) {
            return ValidationResult.failure("Input format is invalid", ThreatLevel.MEDIUM);
        }

        return ValidationResult.success(input);
    }

    private ValidationResult validateContent(String input, ValidationContext context) {
        if (input == null) return ValidationResult.success(null);

        // Check against allowed values if specified
        if (!context.getAllowedValues().isEmpty() && 
            !context.getAllowedValues().contains(input)) {
            return ValidationResult.failure("Value not in allowed list", ThreatLevel.MEDIUM);
        }

        // SQL injection check
        if (sqlInjectionValidator != null && context.isSqlInjectionCheck()) {
            if (!sqlInjectionValidator.isInputSafe(input, context.getFieldName())) {
                return ValidationResult.failure("SQL injection detected", ThreatLevel.HIGH);
            }
        }

        // XSS check
        if (xssProtectionService != null && context.isXssCheck()) {
            try {
                xssProtectionService.sanitizeInput(input);
            } catch (XSSProtectionService.XSSValidationException e) {
                return ValidationResult.failure("XSS attempt detected", ThreatLevel.HIGH);
            }
        }

        return ValidationResult.success(input);
    }

    private ValidationResult validateBusinessLogic(String input, ValidationContext context) {
        // Implement business-specific validation rules
        // This can be extended based on specific requirements
        return ValidationResult.success(input);
    }

    private String applySanitizationRules(String input, ValidationContext context) {
        if (input == null) return null;

        String sanitized = input;

        // Trim whitespace
        sanitized = sanitized.trim();

        // Remove or replace dangerous characters
        if (context.isStrictMode()) {
            sanitized = sanitized.replaceAll("[<>\"'&]", "");
        }

        // Normalize Unicode
        sanitized = java.text.Normalizer.normalize(sanitized, java.text.Normalizer.Form.NFC);

        return sanitized;
    }

    private boolean isRateLimited(ValidationContext context) {
        if (!rateLimitEnabled || context.getClientId() == null) {
            return false;
        }

        String rateLimitKey = "validation:rate_limit:" + context.getClientId();
        String violationCount = (String) redisTemplate.opsForValue().get(rateLimitKey);
        
        if (violationCount != null && Integer.parseInt(violationCount) > maxViolationsPerHour) {
            return true;
        }

        return false;
    }

    private double detectValidationAnomalies(String input, ValidationContext context) {
        double anomalyScore = 0.0;

        // Check input length anomalies
        int averageLength = getAverageInputLength(context.getFieldName());
        if (input.length() > averageLength * 3) {
            anomalyScore += 20.0;
        }

        // Check character distribution
        if (hasUnusualCharacterDistribution(input)) {
            anomalyScore += 15.0;
        }

        // Check input frequency
        if (isRepeatedInput(input, context)) {
            anomalyScore += 10.0;
        }

        return anomalyScore;
    }

    private void recordValidationMetrics(ValidationContext context, ValidationResult result) {
        try {
            ValidationMetric metric = ValidationMetric.builder()
                .fieldName(context.getFieldName())
                .validationType(context.getValidationType().name())
                .valid(result.isValid())
                .threatLevel(result.getThreatLevel().name())
                .timestamp(Instant.now())
                .clientId(context.getClientId())
                .build();

            String metricKey = "validation:metrics:" + context.getFieldName() + ":" + 
                Instant.now().toEpochMilli();
            
            redisTemplate.opsForValue().set(metricKey, objectMapper.writeValueAsString(metric), 
                Duration.ofDays(7));
                
        } catch (Exception e) {
            log.warn("Failed to record validation metrics", e);
        }
    }

    private void updateThreatIntelligence(String input, ValidationContext context, ValidationResult result) {
        if (result.getThreatLevel() == ThreatLevel.HIGH) {
            String rateLimitKey = "validation:rate_limit:" + context.getClientId();
            redisTemplate.opsForValue().increment(rateLimitKey);
            redisTemplate.expire(rateLimitKey, Duration.ofHours(1));
        }
    }

    private boolean isValidCurrency(String currency) {
        Set<String> validCurrencies = Set.of("USD", "EUR", "GBP", "JPY", "NGN", "CAD", "AUD");
        return validCurrencies.contains(currency.toUpperCase());
    }

    private boolean isCommonPassword(String password) {
        Set<String> commonPasswords = Set.of(
            "password", "123456", "password123", "admin", "qwerty", 
            "letmein", "welcome", "monkey", "dragon", "password1"
        );
        return commonPasswords.contains(password.toLowerCase());
    }

    private boolean containsSequentialCharacters(String password) {
        for (int i = 0; i < password.length() - 2; i++) {
            char c1 = password.charAt(i);
            char c2 = password.charAt(i + 1);
            char c3 = password.charAt(i + 2);
            
            if (c2 == c1 + 1 && c3 == c2 + 1) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSuspiciousPatterns(String input) {
        String[] suspiciousPatterns = {
            "script", "javascript", "vbscript", "onload", "onerror",
            "eval", "exec", "system", "cmd", "shell"
        };
        
        String lowerInput = input.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }

    private int getAverageInputLength(String fieldName) {
        // This would typically query historical data
        // For now, return a reasonable default
        return 50;
    }

    private boolean hasUnusualCharacterDistribution(String input) {
        // Simple check for unusual character patterns
        long uniqueChars = input.chars().distinct().count();
        return uniqueChars < input.length() * 0.3; // Less than 30% unique characters
    }

    private boolean isRepeatedInput(String input, ValidationContext context) {
        String recentInputKey = "validation:recent:" + context.getFieldName();
        Set<String> recentInputs = (Set<String>) redisTemplate.opsForValue().get(recentInputKey);
        
        if (recentInputs != null && recentInputs.contains(input)) {
            return true;
        }
        
        return false;
    }

    // Data classes and enums

    @Data
    @Builder(toBuilder = true)
    public static class ValidationContext {
        private String fieldName;
        private ValidationType validationType;
        private boolean required;
        private int minLength;
        private int maxLength;
        private Pattern pattern;
        private Set<String> allowedValues;
        private boolean sqlInjectionCheck;
        private boolean xssCheck;
        private boolean strictMode;
        private String clientId;
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String message;
        private String sanitizedValue;
        private ThreatLevel threatLevel;
        private List<String> violations;

        public static ValidationResult success(String sanitizedValue) {
            return ValidationResult.builder()
                .valid(true)
                .sanitizedValue(sanitizedValue)
                .threatLevel(ThreatLevel.NONE)
                .build();
        }

        public static ValidationResult failure(String message, ThreatLevel threatLevel) {
            return ValidationResult.builder()
                .valid(false)
                .message(message)
                .threatLevel(threatLevel)
                .build();
        }
    }

    @Data
    @Builder
    public static class ValidationRule {
        private String fieldName;
        private ValidationType validationType;
        private boolean required;
        private int minLength;
        private int maxLength;
        private Pattern pattern;
        private Set<String> allowedValues;
    }

    @Data
    @Builder
    public static class ValidationMetric {
        private String fieldName;
        private String validationType;
        private boolean valid;
        private String threatLevel;
        private Instant timestamp;
        private String clientId;
    }

    public enum ValidationType {
        EMAIL, PHONE, ALPHANUMERIC, NUMERIC, UUID, ISO_DATE, 
        SAFE_TEXT, USERNAME, PASSWORD_STRENGTH, URL, FILENAME, RICH_TEXT
    }

    public enum ThreatLevel {
        NONE, LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum DateValidationType {
        BIRTH_DATE, FUTURE_DATE, PAST_DATE, RECENT_DATE, ANY
    }

    public enum ApiKeyType {
        JWT, UUID_BASED, CUSTOM
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    // Validation annotations for Spring integration

    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidatedInput {
        ValidationType type() default ValidationType.SAFE_TEXT;
        boolean required() default false;
        int minLength() default 0;
        int maxLength() default Integer.MAX_VALUE;
        String pattern() default "";
        String[] allowedValues() default {};
        boolean sqlInjectionCheck() default true;
        boolean xssCheck() default true;
    }
}