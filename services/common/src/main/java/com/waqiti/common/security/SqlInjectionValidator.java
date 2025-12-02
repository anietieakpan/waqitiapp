package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Production-Grade SQL Injection Validation Service
 * 
 * Provides comprehensive validation to prevent SQL injection attacks
 * with multiple layers of defense:
 * - Pattern-based detection
 * - Keyword blacklisting
 * - Character validation
 * - Query structure analysis
 * - Audit logging
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-16
 */
@Slf4j
@Component
public class SqlInjectionValidator {
    
    // SQL injection pattern detection
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*\\b(union|select|insert|update|delete|drop|create|alter|exec|execute|" +
        "script|javascript|vbscript|onload|onerror|onclick|<script|<iframe|<object|" +
        "cmd|command|concat|char|substr|ascii|declare|cast|convert|meta|set|" +
        "xp_cmdshell|sp_executesql|information_schema|sysobjects|syscolumns|" +
        "table_name|column_name|schema_name|database|waitfor|delay|benchmark|sleep)\\b.*",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );
    
    // Advanced SQL injection patterns
    private static final Pattern ADVANCED_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(" +
        "--|#|/\\*|\\*/|@@|@|" +                    // SQL comments and variables
        "\\bchar\\s*\\(|\\bnchar\\s*\\(|" +         // String functions
        "\\bvarchar\\s*\\(|\\bnvarchar\\s*\\(|" +   
        "\\balter\\s+|\\bcreate\\s+|\\bdrop\\s+|" + // DDL commands
        "\\bgrant\\s+|\\brevoke\\s+|" +             // Permission changes
        "'\\s*or\\s+'|'\\s*or\\s+1\\s*=\\s*1|" +    // Classic injection
        "1\\s*=\\s*1|'\\s*=\\s*'|" +                // Tautologies
        "admin'\\s*--|'\\s*or\\s*'|" +              // Admin bypass attempts
        "';\\s*shutdown|';\\s*drop|" +              // Destructive commands
        "\\binto\\s+(outfile|dumpfile)|" +          // File operations
        "\\bload_file\\s*\\(" +                     // File reading
        ").*",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );
    
    // Dangerous SQL keywords
    private static final Set<String> DANGEROUS_KEYWORDS = new HashSet<>(Arrays.asList(
        "UNION", "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
        "EXEC", "EXECUTE", "SCRIPT", "JAVASCRIPT", "VBSCRIPT", "XP_CMDSHELL",
        "SP_EXECUTESQL", "INFORMATION_SCHEMA", "SYSOBJECTS", "SYSCOLUMNS",
        "WAITFOR", "DELAY", "BENCHMARK", "SLEEP", "SHUTDOWN", "GRANT", "REVOKE"
    ));
    
    // Suspicious character sequences
    private static final Pattern SUSPICIOUS_CHARS = Pattern.compile(
        "[<>\"'%;()&+\\-]|\\bOR\\b|\\bAND\\b|\\bNOT\\b"
    );
    
    // Encoded injection attempts
    private static final Pattern ENCODED_INJECTION = Pattern.compile(
        "(%27|'|\\-\\-|%23|#|%2D%2D|%2F\\*|\\*%2F|%40|@@|%40%40|" +
        "%3B|;|%3D|=|%2B|\\+|%22|\"|%3C|<|%3E|>|%28|\\(|%29|\\))"
    );
    
    /**
     * Comprehensive SQL injection validation
     * 
     * @param input User input to validate
     * @param fieldName Name of field for logging
     * @return true if input is safe, false if potential injection detected
     */
    public boolean isInputSafe(String input, String fieldName) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }
        
        // Trim and prepare input
        String trimmedInput = input.trim();
        
        // Check against basic injection pattern
        if (SQL_INJECTION_PATTERN.matcher(trimmedInput).matches()) {
            log.error("SQL_INJECTION_DETECTED: Basic pattern match - field: {}, input: {}", 
                fieldName, sanitizeForLogging(input));
            return false;
        }
        
        // Check against advanced injection pattern
        if (ADVANCED_INJECTION_PATTERN.matcher(trimmedInput).matches()) {
            log.error("SQL_INJECTION_DETECTED: Advanced pattern match - field: {}, input: {}", 
                fieldName, sanitizeForLogging(input));
            return false;
        }
        
        // Check for encoded injection attempts
        if (ENCODED_INJECTION.matcher(trimmedInput).find()) {
            log.warn("SQL_INJECTION_SUSPECTED: Encoded pattern detected - field: {}, input: {}", 
                fieldName, sanitizeForLogging(input));
            return false;
        }
        
        // Check for dangerous keywords (case-insensitive)
        String upperInput = trimmedInput.toUpperCase();
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperInput.contains(keyword)) {
                log.error("SQL_INJECTION_DETECTED: Dangerous keyword '{}' found - field: {}", 
                    keyword, fieldName);
                return false;
            }
        }
        
        // Check for multiple suspicious characters
        int suspiciousCount = countSuspiciousCharacters(trimmedInput);
        if (suspiciousCount > 3) {
            log.warn("SQL_INJECTION_SUSPECTED: Too many suspicious characters ({}) - field: {}", 
                suspiciousCount, fieldName);
            return false;
        }
        
        // Check for comment sequences
        if (containsSqlComments(trimmedInput)) {
            log.error("SQL_INJECTION_DETECTED: SQL comments found - field: {}", fieldName);
            return false;
        }
        
        // Check for stacked queries
        if (containsStackedQueries(trimmedInput)) {
            log.error("SQL_INJECTION_DETECTED: Stacked queries detected - field: {}", fieldName);
            return false;
        }
        
        log.debug("Input validation passed for field: {}", fieldName);
        return true;
    }
    
    /**
     * Validate search query input
     * 
     * @param searchQuery Search query to validate
     * @return true if safe
     */
    public boolean isSearchQuerySafe(String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return true;
        }
        
        // More lenient validation for search queries
        String query = searchQuery.trim().toUpperCase();
        
        // Check for obvious injection attempts
        if (query.contains("UNION") || query.contains("SELECT") || 
            query.contains("DROP") || query.contains("DELETE") ||
            query.contains("--") || query.contains("/*") || query.contains("*/")) {
            log.error("SQL_INJECTION_IN_SEARCH: Dangerous pattern in search query: {}", 
                sanitizeForLogging(searchQuery));
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate numeric input
     * 
     * @param input Input that should be numeric
     * @param fieldName Field name for logging
     * @return true if input is valid numeric
     */
    public boolean isNumericInputSafe(String input, String fieldName) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }
        
        // Check if input is purely numeric
        if (!input.matches("^-?\\d+(\\.\\d+)?$")) {
            log.error("INVALID_NUMERIC_INPUT: Non-numeric characters in field: {}, input: {}", 
                fieldName, sanitizeForLogging(input));
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate identifier (table name, column name, etc.)
     * 
     * @param identifier Identifier to validate
     * @param identifierType Type of identifier
     * @return true if safe
     */
    public boolean isIdentifierSafe(String identifier, String identifierType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        
        // Identifiers should only contain alphanumeric and underscore
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            log.error("UNSAFE_IDENTIFIER: Invalid {} name: {}", 
                identifierType, sanitizeForLogging(identifier));
            return false;
        }
        
        // Check against SQL keywords
        if (DANGEROUS_KEYWORDS.contains(identifier.toUpperCase())) {
            log.error("UNSAFE_IDENTIFIER: {} name is SQL keyword: {}", 
                identifierType, identifier);
            return false;
        }
        
        return true;
    }
    
    /**
     * Sanitize user input by removing dangerous characters
     * 
     * @param input Input to sanitize
     * @return Sanitized input
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove SQL comments
        String sanitized = input.replaceAll("--|#|/\\*|\\*/", "");
        
        // Remove or escape special characters
        sanitized = sanitized.replaceAll("[';\"\\\\]", "");
        
        // Remove multiple spaces
        sanitized = sanitized.replaceAll("\\s+", " ");
        
        return sanitized.trim();
    }
    
    /**
     * Quote SQL identifier safely
     * 
     * @param identifier Identifier to quote
     * @return Safely quoted identifier
     */
    public String quoteSqlIdentifier(String identifier) {
        if (!isIdentifierSafe(identifier, "identifier")) {
            throw new SecurityException("Unsafe identifier: " + identifier);
        }
        
        // Use backticks for MySQL or double quotes for PostgreSQL
        return "`" + identifier + "`";
    }
    
    /**
     * Check if input contains SQL comments
     */
    private boolean containsSqlComments(String input) {
        return input.contains("--") || 
               input.contains("#") || 
               input.contains("/*") || 
               input.contains("*/");
    }
    
    /**
     * Check if input contains stacked queries
     */
    private boolean containsStackedQueries(String input) {
        // Look for semicolons that might indicate stacked queries
        String cleaned = input.replaceAll("\\s+", "");
        return cleaned.contains(";") && 
               (cleaned.contains("SELECT") || 
                cleaned.contains("UPDATE") || 
                cleaned.contains("DELETE") ||
                cleaned.contains("INSERT") ||
                cleaned.contains("DROP"));
    }
    
    /**
     * Count suspicious characters in input
     */
    private int countSuspiciousCharacters(String input) {
        int count = 0;
        for (char c : input.toCharArray()) {
            if (c == '\'' || c == '"' || c == ';' || c == '-' || 
                c == '/' || c == '*' || c == '=' || c == '<' || 
                c == '>' || c == '(' || c == ')' || c == '%') {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Sanitize input for safe logging
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        // Truncate long inputs
        if (input.length() > 100) {
            input = input.substring(0, 100) + "...";
        }
        
        // Remove any control characters
        return input.replaceAll("[\\p{Cntrl}]", "");
    }
}