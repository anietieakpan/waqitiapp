package com.waqiti.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * SafeString Validator Implementation
 *
 * Validates strings against XSS, SQL injection, and other common attacks
 */
@Slf4j
public class SafeStringValidator implements ConstraintValidator<SafeString, String> {

    // XSS Attack Patterns
    private static final Pattern SCRIPT_TAG_PATTERN =
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern JAVASCRIPT_PROTOCOL_PATTERN =
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);

    private static final Pattern ON_EVENT_PATTERN =
        Pattern.compile("\\s+on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    private static final Pattern IFRAME_PATTERN =
        Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern OBJECT_EMBED_PATTERN =
        Pattern.compile("<(object|embed)[^>]*>", Pattern.CASE_INSENSITIVE);

    // SQL Injection Patterns
    private static final Pattern SQL_COMMENT_PATTERN =
        Pattern.compile("(--|/\\*|\\*/|;\\s*--)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_UNION_PATTERN =
        Pattern.compile("\\bunion\\b.*\\bselect\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_INJECTION_PATTERN =
        Pattern.compile("('\\s*(or|and)\\s*'?\\d*'?\\s*=\\s*'?\\d*|'\\s*(or|and)\\s*\\d*\\s*=\\s*\\d*)",
            Pattern.CASE_INSENSITIVE);

    // Path Traversal Patterns
    private static final Pattern PATH_TRAVERSAL_PATTERN =
        Pattern.compile("\\.\\./|\\.\\.\\\\/");

    // Command Injection Patterns
    private static final Pattern COMMAND_INJECTION_PATTERN =
        Pattern.compile("[;&|`$]\\s*(rm|cat|ls|wget|curl|bash|sh|exec|eval)", Pattern.CASE_INSENSITIVE);

    private boolean allowHtml;
    private boolean allowSpecialChars;
    private int maxLength;

    @Override
    public void initialize(SafeString constraintAnnotation) {
        this.allowHtml = constraintAnnotation.allowHtml();
        this.allowSpecialChars = constraintAnnotation.allowSpecialChars();
        this.maxLength = constraintAnnotation.maxLength();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are valid (use @NotNull for null checks)
        if (value == null || value.isEmpty()) {
            return true;
        }

        // Check max length if specified
        if (maxLength > 0 && value.length() > maxLength) {
            setCustomMessage(context,
                String.format("String exceeds maximum length of %d characters", maxLength));
            return false;
        }

        // Check for XSS attacks
        if (!isXssSafe(value)) {
            setCustomMessage(context, "Potential XSS attack detected");
            log.warn("SECURITY: XSS attempt blocked: {}", sanitizeForLogging(value));
            return false;
        }

        // Check for SQL injection
        if (!isSqlSafe(value)) {
            setCustomMessage(context, "Potential SQL injection detected");
            log.warn("SECURITY: SQL injection attempt blocked: {}", sanitizeForLogging(value));
            return false;
        }

        // Check for path traversal
        if (!isPathSafe(value)) {
            setCustomMessage(context, "Potential path traversal detected");
            log.warn("SECURITY: Path traversal attempt blocked: {}", sanitizeForLogging(value));
            return false;
        }

        // Check for command injection
        if (!isCommandSafe(value)) {
            setCustomMessage(context, "Potential command injection detected");
            log.warn("SECURITY: Command injection attempt blocked: {}", sanitizeForLogging(value));
            return false;
        }

        // If HTML is not allowed, check for any HTML tags
        if (!allowHtml && containsHtml(value)) {
            setCustomMessage(context, "HTML tags are not allowed");
            return false;
        }

        // If HTML is allowed, sanitize it
        if (allowHtml) {
            String sanitized = sanitizeHtml(value);
            if (!sanitized.equals(value)) {
                log.info("SECURITY: HTML sanitized - some tags/attributes were removed");
                // This is a warning, not a failure - sanitization should happen in service layer
            }
        }

        return true;
    }

    /**
     * Check for XSS attack patterns
     */
    private boolean isXssSafe(String value) {
        // Check for script tags
        if (SCRIPT_TAG_PATTERN.matcher(value).find()) {
            return false;
        }

        // Check for javascript: protocol
        if (JAVASCRIPT_PROTOCOL_PATTERN.matcher(value).find()) {
            return false;
        }

        // Check for event handlers (onclick, onerror, etc.)
        if (ON_EVENT_PATTERN.matcher(value).find()) {
            return false;
        }

        // Check for iframe tags
        if (IFRAME_PATTERN.matcher(value).find()) {
            return false;
        }

        // Check for object/embed tags
        if (OBJECT_EMBED_PATTERN.matcher(value).find()) {
            return false;
        }

        return true;
    }

    /**
     * Check for SQL injection patterns
     */
    private boolean isSqlSafe(String value) {
        // Check for SQL comments
        if (SQL_COMMENT_PATTERN.matcher(value).find()) {
            return false;
        }

        // Check for UNION SELECT attacks
        if (SQL_UNION_PATTERN.matcher(value).find()) {
            return false;
        }

        // Check for common SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(value).find()) {
            return false;
        }

        return true;
    }

    /**
     * Check for path traversal attacks
     */
    private boolean isPathSafe(String value) {
        return !PATH_TRAVERSAL_PATTERN.matcher(value).find();
    }

    /**
     * Check for command injection
     */
    private boolean isCommandSafe(String value) {
        return !COMMAND_INJECTION_PATTERN.matcher(value).find();
    }

    /**
     * Check if string contains HTML
     */
    private boolean containsHtml(String value) {
        // Simple check for common HTML tags
        return value.contains("<") && value.contains(">");
    }

    /**
     * Sanitize HTML using JSoup with safe whitelist
     */
    private String sanitizeHtml(String value) {
        // Allow only safe HTML tags and attributes
        Safelist safelist = Safelist.basic()
            .addTags("p", "br", "strong", "em", "u")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https"); // No javascript: protocol

        return Jsoup.clean(value, safelist);
    }

    /**
     * Sanitize value for logging (truncate and escape)
     */
    private String sanitizeForLogging(String value) {
        if (value == null) return "null";

        String sanitized = value.replaceAll("[<>\"']", "")
                                .replaceAll("\\r|\\n", " ");

        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100) + "...";
        }

        return sanitized;
    }

    /**
     * Set custom validation message
     */
    private void setCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
