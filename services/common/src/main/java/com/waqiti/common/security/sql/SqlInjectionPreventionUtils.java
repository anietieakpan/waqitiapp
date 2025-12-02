package com.waqiti.common.security.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL Injection Prevention Utilities
 * 
 * Provides utility methods to prevent SQL injection attacks across all services.
 * These utilities should be used when dynamic SQL construction is unavoidable.
 */
@Slf4j
@Component
public class SqlInjectionPreventionUtils {

    // Common SQL injection patterns
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
        Pattern.compile("('|(\\-\\-)|(;)|(\\|)|(\\*)|(\\%))|(union)|(select)|(insert)|(delete)|(update)|(drop)|(create)|(alter)|(exec)|(execute)|(script)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(javascript:|vbscript:|onload|onerror|onclick)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(<script|</script|<iframe|</iframe)", Pattern.CASE_INSENSITIVE)
    );

    // Allowed characters for different types of inputs
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s-()]+$");
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    
    // Valid sort column names and orders
    private static final List<String> VALID_SORT_COLUMNS = List.of(
        "id", "createdAt", "updatedAt", "name", "email", "status", "amount", "date",
        "username", "firstName", "lastName", "phoneNumber", "transactionId"
    );
    
    private static final List<String> VALID_SORT_ORDERS = List.of("ASC", "DESC");
    
    // Valid time intervals for analytics
    private static final List<String> VALID_TIME_INTERVALS = List.of("HOUR", "DAY", "WEEK", "MONTH", "YEAR");

    /**
     * Check if a string contains potential SQL injection patterns
     */
    public boolean containsSqlInjection(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        String normalizedInput = input.trim().toLowerCase();
        
        return SQL_INJECTION_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(normalizedInput).find());
    }

    /**
     * Sanitize string input for safe use in SQL queries
     */
    public String sanitizeStringInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove potentially dangerous characters
        return input.replaceAll("[';\\-\\-/*]", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Validate and sanitize alphanumeric input (IDs, codes, etc.)
     */
    public String validateAlphanumeric(String input, String fieldName) {
        if (input == null) {
            return null;
        }
        
        if (!ALPHANUMERIC_PATTERN.matcher(input).matches()) {
            log.warn("Invalid alphanumeric input for field {}: {}", fieldName, input);
            throw new IllegalArgumentException("Invalid " + fieldName + " format");
        }
        
        return input;
    }

    /**
     * Validate email format
     */
    public String validateEmail(String email) {
        if (email == null) {
            return null;
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            log.warn("Invalid email format: {}", email);
            throw new IllegalArgumentException("Invalid email format");
        }
        
        return email.toLowerCase().trim();
    }

    /**
     * Validate phone number format
     */
    public String validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            log.warn("Invalid phone number format: {}", phoneNumber);
            throw new IllegalArgumentException("Invalid phone number format");
        }
        
        return phoneNumber.replaceAll("[\\s-()]", "");
    }

    /**
     * Validate UUID format
     */
    public String validateUuid(String uuid, String fieldName) {
        if (uuid == null) {
            return null;
        }
        
        if (!UUID_PATTERN.matcher(uuid).matches()) {
            log.warn("Invalid UUID format for field {}: {}", fieldName, uuid);
            throw new IllegalArgumentException("Invalid " + fieldName + " format");
        }
        
        return uuid.toLowerCase();
    }

    /**
     * Validate sort column name
     */
    public String validateSortColumn(String column) {
        if (column == null) {
            return "createdAt"; // Default sort column
        }
        
        String normalizedColumn = column.trim().toLowerCase();
        
        if (!VALID_SORT_COLUMNS.contains(normalizedColumn)) {
            log.warn("Invalid sort column: {}. Using default.", column);
            return "createdAt";
        }
        
        return normalizedColumn;
    }

    /**
     * Validate sort order
     */
    public String validateSortOrder(String order) {
        if (order == null) {
            return "DESC"; // Default sort order
        }
        
        String normalizedOrder = order.trim().toUpperCase();
        
        if (!VALID_SORT_ORDERS.contains(normalizedOrder)) {
            log.warn("Invalid sort order: {}. Using DESC.", order);
            return "DESC";
        }
        
        return normalizedOrder;
    }

    /**
     * Validate time interval for analytics
     */
    public String validateTimeInterval(String interval) {
        if (interval == null) {
            return "DAY"; // Default interval
        }
        
        String normalizedInterval = interval.trim().toUpperCase();
        
        if (!VALID_TIME_INTERVALS.contains(normalizedInterval)) {
            log.warn("Invalid time interval: {}. Using DAY.", interval);
            return "DAY";
        }
        
        return normalizedInterval;
    }

    /**
     * Validate limit parameter for pagination
     */
    public int validateLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        
        if (limit > maxLimit) {
            log.warn("Limit {} exceeds maximum {}. Using maximum.", limit, maxLimit);
            return maxLimit;
        }
        
        return limit;
    }

    /**
     * Validate offset parameter for pagination
     */
    public int validateOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return 0;
        }
        
        return offset;
    }

    /**
     * Escape special characters for LIKE queries
     */
    public String escapeLikePattern(String pattern) {
        if (pattern == null) {
            return null;
        }
        
        return pattern.replace("\\", "\\\\")
                     .replace("%", "\\%")
                     .replace("_", "\\_")
                     .replace("[", "\\[");
    }

    /**
     * Create safe LIKE pattern with wildcards
     */
    public String createLikePattern(String searchTerm, boolean prefixMatch, boolean suffixMatch) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return "%";
        }
        
        String escaped = escapeLikePattern(searchTerm.trim());
        
        StringBuilder pattern = new StringBuilder();
        if (prefixMatch) {
            pattern.append("%");
        }
        pattern.append(escaped);
        if (suffixMatch) {
            pattern.append("%");
        }
        
        return pattern.toString();
    }

    /**
     * Validate table name for dynamic queries (use with extreme caution)
     */
    public String validateTableName(String tableName, List<String> allowedTables) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        String normalized = tableName.trim().toLowerCase();
        
        if (!ALPHANUMERIC_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid table name format: " + tableName);
        }
        
        if (!allowedTables.contains(normalized)) {
            throw new IllegalArgumentException("Table name not in allowed list: " + tableName);
        }
        
        return normalized;
    }

    /**
     * Validate column name for dynamic queries (use with extreme caution)
     */
    public String validateColumnName(String columnName, List<String> allowedColumns) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be null or empty");
        }
        
        String normalized = columnName.trim().toLowerCase();
        
        if (!ALPHANUMERIC_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid column name format: " + columnName);
        }
        
        if (!allowedColumns.contains(normalized)) {
            throw new IllegalArgumentException("Column name not in allowed list: " + columnName);
        }
        
        return normalized;
    }

    /**
     * Log potential SQL injection attempt
     */
    public void logSqlInjectionAttempt(String input, String source, String userId) {
        log.warn("SECURITY ALERT: Potential SQL injection attempt detected. " +
                "Input: [{}], Source: [{}], User: [{}]", 
                sanitizeForLogging(input), source, userId);
    }

    /**
     * Sanitize input for safe logging (prevent log injection)
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        return input.replaceAll("[\r\n\t]", "_")
                   .substring(0, Math.min(input.length(), 100)); // Limit length
    }
}