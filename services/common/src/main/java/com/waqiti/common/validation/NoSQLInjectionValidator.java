package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Production-ready SQL injection prevention validator
 */
@Component
public class NoSQLInjectionValidator implements ConstraintValidator<ValidationConstraints.NoSQLInjection, String> {
    
    // SQL keywords and patterns that could indicate injection attempts
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(" +
        // SQL Commands
        "SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|" +
        "UNION|FROM|WHERE|JOIN|ORDER\\s+BY|GROUP\\s+BY|HAVING|" +
        // SQL Functions and operators
        "CONCAT|SUBSTRING|ASCII|CHAR|COUNT|SUM|AVG|MAX|MIN|" +
        "CAST|CONVERT|COALESCE|NULLIF|IIF|CASE|" +
        // Database specific
        "XP_CMDSHELL|SP_EXECUTESQL|OPENROWSET|OPENDATASOURCE|" +
        "INFORMATION_SCHEMA|SYSOBJECTS|SYSCOLUMNS|" +
        // Common injection patterns
        "--|#|/\\*|\\*/|@@|@|" +
        // Hex encoding
        "0x[0-9a-fA-F]+|" +
        // String concatenation attempts
        "\\|\\||\\+|" +
        // Escape attempts
        "\\\\x[0-9a-fA-F]{2}|\\\\[0-7]{1,3}|" +
        // Time-based blind SQL injection
        "WAITFOR|DELAY|BENCHMARK|SLEEP|" +
        // Boolean-based blind SQL injection
        "AND\\s+\\d+\\s*=\\s*\\d+|OR\\s+\\d+\\s*=\\s*\\d+|" +
        "AND\\s+'[^']*'\\s*=\\s*'[^']*'|OR\\s+'[^']*'\\s*=\\s*'[^']*'|" +
        // Common bypass attempts
        "CHAR\\(|CHR\\(|CONCAT\\(|" +
        // NoSQL injection patterns (MongoDB, etc.)
        "\\$ne|\\$eq|\\$gt|\\$gte|\\$lt|\\$lte|\\$in|\\$nin|" +
        "\\$exists|\\$type|\\$regex|\\$where|\\$expr|" +
        "\\{\\s*['\"]?\\$|" +
        // LDAP injection patterns
        "\\*\\||\\|\\*|\\(\\||\\|\\)|" +
        ")"
    );
    
    // Additional suspicious patterns
    private static final Pattern SUSPICIOUS_PATTERNS = Pattern.compile(
        "(?i)(" +
        // Semicolon followed by SQL command
        ";\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE)|" +
        // Quote followed by SQL operators
        "'\\s*(OR|AND|UNION)|" +
        // Common SQL injection endings
        "'\\s*--\\s*|" +
        "'\\s*#|" +
        "'\\s*/\\*|" +
        // Stacked queries
        ";\\s*[A-Z]+\\s+|" +
        // Function calls with parentheses
        "[A-Z_]+\\s*\\([^)]*\\)|" +
        // System tables/views
        "sys\\.|mysql\\.|information_schema\\.|" +
        // File operations
        "INTO\\s+OUTFILE|LOAD_FILE|" +
        ")"
    );
    
    @Override
    public void initialize(ValidationConstraints.NoSQLInjection annotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains potentially dangerous SQL patterns"
            ).addConstraintViolation();
            return false;
        }
        
        // Check for suspicious patterns
        if (SUSPICIOUS_PATTERNS.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains suspicious character sequences"
            ).addConstraintViolation();
            return false;
        }
        
        // Check for encoded attacks
        if (containsEncodedAttack(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains encoded attack patterns"
            ).addConstraintViolation();
            return false;
        }
        
        // Check for excessive special characters
        if (hasExcessiveSpecialCharacters(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Input contains excessive special characters"
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean containsEncodedAttack(String value) {
        // Check for URL encoding
        if (value.contains("%27") || value.contains("%22") || // Encoded quotes
            value.contains("%3C") || value.contains("%3E") || // Encoded < >
            value.contains("%00") || // Null byte
            value.contains("%25")) { // Encoded %
            return true;
        }
        
        // Check for Unicode encoding
        if (value.matches(".*\\\\u[0-9a-fA-F]{4}.*")) {
            String decoded = decodeUnicode(value);
            if (SQL_INJECTION_PATTERN.matcher(decoded).find()) {
                return true;
            }
        }
        
        // Check for HTML entity encoding
        if (value.contains("&#") || value.contains("&lt;") || 
            value.contains("&gt;") || value.contains("&quot;") ||
            value.contains("&apos;")) {
            String decoded = decodeHtmlEntities(value);
            if (SQL_INJECTION_PATTERN.matcher(decoded).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasExcessiveSpecialCharacters(String value) {
        int specialCharCount = 0;
        int totalChars = value.length();
        
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                specialCharCount++;
            }
        }
        
        // If more than 30% of characters are special characters, it's suspicious
        double ratio = (double) specialCharCount / totalChars;
        return ratio > 0.3 && specialCharCount > 5;
    }
    
    private String decodeUnicode(String value) {
        return value.replaceAll("\\\\u([0-9a-fA-F]{4})", m -> {
            String hex = m.replaceAll("\\\\u", "");
            int decimal = Integer.parseInt(hex, 16);
            return String.valueOf((char) decimal);
        });
    }
    
    private String decodeHtmlEntities(String value) {
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&#60;", "<")
            .replace("&#62;", ">");
    }
}