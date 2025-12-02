package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Service for preventing SQL injection attacks in user input.
 * 
 * CRITICAL SECURITY: This service provides multiple layers of SQL injection prevention:
 * 1. Pattern-based detection of SQL injection attempts
 * 2. Input sanitization
 * 3. Validation utilities
 * 4. Audit logging of suspicious input
 * 
 * IMPORTANT: This is a DEFENSE IN DEPTH measure. The PRIMARY defense against
 * SQL injection is using parameterized queries / PreparedStatement. This service
 * provides an additional layer of protection.
 * 
 * Usage:
 * <pre>
 * {@code
 * // Before processing user input
 * if (sqlInjectionPreventionService.containsSqlInjection(userInput)) {
 *     throw new SecurityException("SQL injection attempt detected");
 * }
 * 
 * // Or sanitize the input
 * String safeInput = sqlInjectionPreventionService.sanitize(userInput);
 * }
 * </pre>
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Service
@Slf4j
public class SqlInjectionPreventionService {

    /**
     * Common SQL injection patterns
     */
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        // SQL keywords
        Pattern.compile("(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|DECLARE)\\b)", Pattern.CASE_INSENSITIVE),
        
        // SQL comments
        Pattern.compile("(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE),
        
        // SQL operators and special characters
        Pattern.compile("([';]|(\\bOR\\b|\\bAND\\b)\\s*=)", Pattern.CASE_INSENSITIVE),
        
        // Hex injection
        Pattern.compile("(0x[0-9A-F]+)", Pattern.CASE_INSENSITIVE),
        
        // Quote escaping attempts
        Pattern.compile("(\\\\'|\\\\\\\"|''|\\\"\\\")", Pattern.CASE_INSENSITIVE),
        
        // SQL functions
        Pattern.compile("(\\b(CAST|CONVERT|CONCAT|CHAR|ASCII|SUBSTRING|LENGTH|DATABASE|USER|VERSION|BENCHMARK)\\b\\s*\\()", Pattern.CASE_INSENSITIVE),
        
        // Time-based blind SQL injection
        Pattern.compile("(\\b(SLEEP|WAITFOR|DELAY)\\b\\s*\\()", Pattern.CASE_INSENSITIVE),
        
        // Union-based injection
        Pattern.compile("(\\bUNION\\b.*\\bSELECT\\b)", Pattern.CASE_INSENSITIVE),
        
        // Boolean-based injection
        Pattern.compile("(\\b(TRUE|FALSE)\\b\\s*(=|<>|!=))", Pattern.CASE_INSENSITIVE),
        
        // Stacked queries
        Pattern.compile("(;\\s*(SELECT|INSERT|UPDATE|DELETE|DROP)\\b)", Pattern.CASE_INSENSITIVE),
        
        // XML injection (for SQL Server)
        Pattern.compile("(<\\?xml|<sql|<query)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Dangerous SQL keywords that should never appear in user input
     */
    private static final List<String> DANGEROUS_KEYWORDS = Arrays.asList(
        "exec", "execute", "sp_executesql", "xp_cmdshell", "xp_regread", "xp_regwrite",
        "bulk insert", "openrowset", "openquery", "opendatasource"
    );

    /**
     * Check if input contains potential SQL injection
     * 
     * @param input The user input to check
     * @return true if SQL injection detected, false otherwise
     */
    public boolean containsSqlInjection(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String normalizedInput = input.toLowerCase().trim();

        // Check against patterns
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(normalizedInput).find()) {
                logSqlInjectionAttempt(input, "Pattern match: " + pattern.pattern());
                return true;
            }
        }

        // Check dangerous keywords
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (normalizedInput.contains(keyword)) {
                logSqlInjectionAttempt(input, "Dangerous keyword: " + keyword);
                return true;
            }
        }

        return false;
    }

    /**
     * Sanitize input by removing or escaping dangerous characters
     * 
     * WARNING: This should NOT be used as a replacement for parameterized queries!
     * Use this only for additional protection or when parameterized queries cannot be used.
     * 
     * @param input The input to sanitize
     * @return Sanitized input
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        // Remove null bytes
        String sanitized = input.replace("\0", "");

        // Escape single quotes
        sanitized = sanitized.replace("'", "''");

        // Remove SQL comments
        sanitized = sanitized.replaceAll("--", "");
        sanitized = sanitized.replaceAll("/\\*", "");
        sanitized = sanitized.replaceAll("\\*/", "");

        // Remove semicolons (prevents stacked queries)
        sanitized = sanitized.replace(";", "");

        // Remove backslashes (prevents escape sequence attacks)
        sanitized = sanitized.replace("\\", "");

        return sanitized;
    }

    /**
     * Validate that input contains only allowed characters
     * 
     * @param input The input to validate
     * @param allowedPattern Regex pattern of allowed characters
     * @return true if valid, false otherwise
     */
    public boolean isValid(String input, String allowedPattern) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        Pattern pattern = Pattern.compile(allowedPattern);
        return pattern.matcher(input).matches();
    }

    /**
     * Validate alphanumeric input (letters, numbers, spaces, hyphens, underscores only)
     */
    public boolean isAlphanumeric(String input) {
        return isValid(input, "^[a-zA-Z0-9\\s\\-_]+$");
    }

    /**
     * Validate numeric input
     */
    public boolean isNumeric(String input) {
        return isValid(input, "^[0-9]+$");
    }

    /**
     * Validate email format (basic validation)
     */
    public boolean isValidEmail(String input) {
        return isValid(input, "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    /**
     * Validate UUID format
     */
    public boolean isValidUUID(String input) {
        return isValid(input, "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    /**
     * Validate that input length is within acceptable range
     */
    public boolean isValidLength(String input, int minLength, int maxLength) {
        if (input == null) {
            return false;
        }
        int length = input.length();
        return length >= minLength && length <= maxLength;
    }

    /**
     * Comprehensive validation combining multiple checks
     */
    public ValidationResult validateInput(String input, ValidationConfig config) {
        List<String> violations = new ArrayList<>();

        // Check for null/empty
        if (input == null || input.trim().isEmpty()) {
            if (config.isRequired()) {
                violations.add("Input is required");
            }
            return new ValidationResult(!violations.isEmpty(), violations);
        }

        // Check length
        if (!isValidLength(input, config.getMinLength(), config.getMaxLength())) {
            violations.add(String.format("Input length must be between %d and %d characters",
                config.getMinLength(), config.getMaxLength()));
        }

        // Check for SQL injection
        if (config.isCheckSqlInjection() && containsSqlInjection(input)) {
            violations.add("Input contains potential SQL injection");
        }

        // Check pattern if provided
        if (config.getAllowedPattern() != null && !isValid(input, config.getAllowedPattern())) {
            violations.add("Input contains invalid characters");
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }

    /**
     * Log SQL injection attempt for security monitoring
     */
    private void logSqlInjectionAttempt(String input, String reason) {
        log.warn("SECURITY: Potential SQL injection detected - Reason: {} - Input: {}", 
            reason, sanitizeForLogging(input));
        
        // In production, this should also:
        // 1. Send alert to security team
        // 2. Log to SIEM
        // 3. Increment security metrics
        // 4. Potentially block the user
    }

    /**
     * Sanitize input for safe logging (prevent log injection)
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        // Remove newlines and carriage returns to prevent log forging
        return input.replaceAll("[\n\r]", " ")
                   .substring(0, Math.min(input.length(), 100)); // Limit length
    }

    /**
     * Configuration for input validation
     */
    public static class ValidationConfig {
        private boolean required = false;
        private int minLength = 0;
        private int maxLength = Integer.MAX_VALUE;
        private boolean checkSqlInjection = true;
        private String allowedPattern = null;

        public static ValidationConfig builder() {
            return new ValidationConfig();
        }

        public ValidationConfig required(boolean required) {
            this.required = required;
            return this;
        }

        public ValidationConfig minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public ValidationConfig maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public ValidationConfig checkSqlInjection(boolean checkSqlInjection) {
            this.checkSqlInjection = checkSqlInjection;
            return this;
        }

        public ValidationConfig allowedPattern(String allowedPattern) {
            this.allowedPattern = allowedPattern;
            return this;
        }

        public boolean isRequired() { return required; }
        public int getMinLength() { return minLength; }
        public int getMaxLength() { return maxLength; }
        public boolean isCheckSqlInjection() { return checkSqlInjection; }
        public String getAllowedPattern() { return allowedPattern; }
    }

    /**
     * Result of validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> violations;

        public ValidationResult(boolean valid, List<String> violations) {
            this.valid = valid;
            this.violations = violations;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getViolations() {
            return violations;
        }

        public String getViolationsAsString() {
            return String.join("; ", violations);
        }
    }
}