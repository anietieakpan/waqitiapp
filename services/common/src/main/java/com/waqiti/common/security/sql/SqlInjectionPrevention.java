package com.waqiti.common.security.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY: SQL Injection Prevention Utility
 * 
 * This utility provides methods to safely escape user input for SQL queries
 * and detect potential SQL injection attempts.
 */
@Component
@Slf4j
public class SqlInjectionPrevention {

    // Patterns to detect potential SQL injection attempts
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript)" +
        ".*|(--|#|/\\*|\\*/|;|'|\"|`|\\||&|<|>|=|\\+|\\-|\\*|\\(|\\)|\\[|\\]|\\{|\\}|%|_|\\\\).*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern BASIC_SQL_KEYWORDS = Pattern.compile(
        "(?i)\\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMMENT_PATTERNS = Pattern.compile(
        "(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE
    );

    /**
     * Escape SQL wildcards (% and _) for LIKE queries
     * This prevents wildcard injection attacks
     */
    public String escapeSqlWildcards(String input) {
        if (input == null) {
            return null;
        }
        
        return input.replace("\\", "\\\\")  // Escape backslash first
                   .replace("%", "\\%")     // Escape percent wildcard
                   .replace("_", "\\_");    // Escape underscore wildcard
    }

    /**
     * Sanitize search input by removing/escaping dangerous characters
     */
    public String sanitizeSearchInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        
        // First escape SQL wildcards
        String escaped = escapeSqlWildcards(input.trim());
        
        // Remove SQL comments
        escaped = COMMENT_PATTERNS.matcher(escaped).replaceAll("");
        
        // Remove potentially dangerous characters but keep alphanumeric and basic punctuation
        escaped = escaped.replaceAll("[^\\w\\s\\-\\.@]", "");
        
        // Limit length to prevent buffer overflow attacks
        if (escaped.length() > 255) {
            escaped = escaped.substring(0, 255);
        }
        
        return escaped;
    }

    /**
     * Validate that input doesn't contain obvious SQL injection attempts
     * Returns true if input appears safe, false if potentially malicious
     */
    public boolean isInputSafe(String input) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }
        
        String normalizedInput = input.toLowerCase().trim();
        
        // Check for SQL keywords in suspicious contexts
        if (BASIC_SQL_KEYWORDS.matcher(normalizedInput).find()) {
            log.warn("SECURITY: Potential SQL injection attempt detected - SQL keywords found: {}", 
                maskSensitiveInput(input));
            return false;
        }
        
        // Check for SQL comments
        if (COMMENT_PATTERNS.matcher(normalizedInput).find()) {
            log.warn("SECURITY: Potential SQL injection attempt detected - SQL comments found: {}", 
                maskSensitiveInput(input));
            return false;
        }
        
        // Check for multiple single quotes (SQL string escape attempts)
        if (normalizedInput.contains("''") || normalizedInput.contains("\"\"")) {
            log.warn("SECURITY: Potential SQL injection attempt detected - quote escaping found: {}", 
                maskSensitiveInput(input));
            return false;
        }
        
        // Check for semicolons (query termination attempts)
        if (normalizedInput.contains(";")) {
            log.warn("SECURITY: Potential SQL injection attempt detected - semicolon found: {}", 
                maskSensitiveInput(input));
            return false;
        }
        
        return true;
    }

    /**
     * Validate and sanitize search input - throws exception if malicious
     */
    public String validateAndSanitizeSearchInput(String input) throws SecurityException {
        if (!isInputSafe(input)) {
            throw new SecurityException("Input contains potentially malicious content");
        }
        
        return sanitizeSearchInput(input);
    }

    /**
     * Create a safe LIKE pattern for database queries
     * This method properly escapes wildcards and wraps with %
     */
    public String createSafeLikePattern(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "%";
        }
        
        String sanitized = validateAndSanitizeSearchInput(input);
        return "%" + sanitized + "%";
    }

    /**
     * Create a safe ILIKE pattern (case-insensitive) for PostgreSQL
     */
    public String createSafeILikePattern(String input) {
        return createSafeLikePattern(input);
    }

    /**
     * Validate SQL ORDER BY clause to prevent injection
     */
    public boolean isValidOrderByClause(String orderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) {
            return true;
        }
        
        // Only allow alphanumeric, underscore, dot, space, ASC, DESC
        Pattern validOrderBy = Pattern.compile("^[\\w\\s\\.,]+(\\s+(ASC|DESC))?$", Pattern.CASE_INSENSITIVE);
        
        return validOrderBy.matcher(orderBy.trim()).matches() && isInputSafe(orderBy);
    }

    /**
     * Validate that column names are safe (for dynamic ORDER BY)
     */
    public boolean isValidColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            return false;
        }
        
        // Column names should only contain letters, numbers, underscores, and dots
        Pattern validColumn = Pattern.compile("^[a-zA-Z][\\w\\.]*$");
        
        return validColumn.matcher(columnName.trim()).matches();
    }

    /**
     * Sanitize numeric input to prevent injection through number fields
     */
    public String sanitizeNumericInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove everything except digits, decimal point, and minus sign
        return input.replaceAll("[^\\d\\.\\-]", "");
    }

    /**
     * Mask sensitive input for logging (show first and last 2 chars)
     */
    private String maskSensitiveInput(String input) {
        if (input == null || input.length() <= 4) {
            return "***";
        }
        
        return input.substring(0, 2) + "***" + input.substring(input.length() - 2);
    }

    /**
     * Detect if string contains potential SQL injection payload
     */
    public boolean containsSqlInjectionPayload(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        return SQL_INJECTION_PATTERN.matcher(input.toLowerCase()).find();
    }

    /**
     * Log security violation for monitoring
     */
    public void logSecurityViolation(String input, String context, String userAgent) {
        log.error("SECURITY VIOLATION: Potential SQL injection attempt - Context: {} Input: {} UserAgent: {}", 
            context, maskSensitiveInput(input), userAgent);
        
        // In production, this would also send alerts to security team
        // Could integrate with SIEM systems, security incident response, etc.
    }

    /**
     * Validate pagination parameters to prevent injection
     */
    public boolean isValidPaginationParam(Integer value, int maxValue) {
        return value != null && value >= 0 && value <= maxValue;
    }

    /**
     * Create safe limit and offset for pagination
     */
    public String createSafePaginationClause(Integer limit, Integer offset) {
        if (limit == null || limit <= 0) {
            limit = 20; // Default limit
        }
        if (limit > 1000) {
            limit = 1000; // Max limit to prevent resource exhaustion
        }
        
        if (offset == null || offset < 0) {
            offset = 0;
        }
        
        return String.format("LIMIT %d OFFSET %d", limit, offset);
    }
}