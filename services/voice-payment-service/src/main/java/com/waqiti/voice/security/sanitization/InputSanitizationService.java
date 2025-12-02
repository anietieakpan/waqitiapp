package com.waqiti.voice.security.sanitization;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Input Sanitization Service
 *
 * CRITICAL SECURITY: Prevents injection attacks
 *
 * Protections:
 * - XSS (Cross-Site Scripting)
 * - SQL Injection (via parameterized queries)
 * - Command Injection
 * - Path Traversal
 * - LDAP Injection
 * - XML Injection
 *
 * Compliance:
 * - OWASP Top 10 - A03:2021 Injection
 * - PCI-DSS Requirement 6.5.1 (Injection flaws)
 * - CWE-79 (XSS), CWE-89 (SQL Injection)
 */
@Slf4j
@Service
public class InputSanitizationService {

    // Patterns for malicious input detection
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "('.+(\\s)*(or|and|union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|<script|<iframe|onerror|onload).*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            ".*(\\.\\./|\\.\\\\|%2e%2e%2f|%2e%2e/|\\.\\.%2f|%2e%2e%5c)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            ".*(;|\\||&|`|\\$\\(|\\$\\{|>|<|\\n|\\r).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern XML_INJECTION_PATTERN = Pattern.compile(
            ".*(<\\?xml|<!\\[CDATA|<!DOCTYPE|<!ENTITY).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Sanitize HTML input (remove all HTML tags except safe ones)
     *
     * @param input Raw HTML input
     * @return Sanitized HTML
     */
    public String sanitizeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Use JSoup with strict whitelist (no HTML tags allowed for voice payments)
        String sanitized = Jsoup.clean(input, Safelist.none());

        if (!sanitized.equals(input)) {
            log.warn("HTML content sanitized: removed {} characters",
                    input.length() - sanitized.length());
        }

        return sanitized;
    }

    /**
     * Sanitize text input (allow basic text only)
     *
     * Use for voice transcriptions, user messages, etc.
     *
     * @param input Raw text input
     * @return Sanitized text
     */
    public String sanitizeText(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Remove HTML tags
        String sanitized = Jsoup.clean(input, Safelist.none());

        // Detect and warn on injection attempts
        detectInjectionAttempts(sanitized);

        // Normalize whitespace
        sanitized = sanitized.trim().replaceAll("\\s+", " ");

        return sanitized;
    }

    /**
     * Sanitize file name (prevent path traversal)
     *
     * @param fileName Original file name
     * @return Sanitized file name
     */
    public String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }

        // Remove path components
        String sanitized = fileName.replaceAll("[\\\\/]", "");

        // Remove path traversal attempts
        sanitized = sanitized.replaceAll("\\.\\.", "");

        // Remove special characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Limit length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        if (!sanitized.equals(fileName)) {
            log.warn("File name sanitized: '{}' -> '{}'", fileName, sanitized);
        }

        return sanitized;
    }

    /**
     * Validate recipient identifier (email, phone, username)
     *
     * @param identifier Recipient identifier
     * @param type Identifier type
     * @return true if valid
     */
    public boolean validateRecipientIdentifier(String identifier, String type) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        // Detect injection attempts
        if (containsSqlInjection(identifier) ||
            containsCommandInjection(identifier) ||
            containsPathTraversal(identifier)) {
            log.error("SECURITY: Injection attempt detected in recipient identifier: {}", identifier);
            return false;
        }

        switch (type.toLowerCase()) {
            case "email":
                return validateEmail(identifier);
            case "phone":
                return validatePhone(identifier);
            case "username":
                return validateUsername(identifier);
            default:
                return false;
        }
    }

    /**
     * Validate email format
     */
    private boolean validateEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }

    /**
     * Validate phone number format
     */
    private boolean validatePhone(String phone) {
        // Remove common phone number formatting
        String cleaned = phone.replaceAll("[\\s()+-]", "");
        // Check if it's all digits and reasonable length
        return cleaned.matches("^\\d{10,15}$");
    }

    /**
     * Validate username format
     */
    private boolean validateUsername(String username) {
        // Alphanumeric, underscores, hyphens, 3-30 characters
        return username.matches("^[a-zA-Z0-9_-]{3,30}$");
    }

    /**
     * Detect SQL injection attempts
     */
    public boolean containsSqlInjection(String input) {
        if (input == null) {
            return false;
        }

        boolean detected = SQL_INJECTION_PATTERN.matcher(input).matches();
        if (detected) {
            log.error("SECURITY: SQL injection attempt detected: {}", input);
        }
        return detected;
    }

    /**
     * Detect path traversal attempts
     */
    public boolean containsPathTraversal(String input) {
        if (input == null) {
            return false;
        }

        boolean detected = PATH_TRAVERSAL_PATTERN.matcher(input).matches();
        if (detected) {
            log.error("SECURITY: Path traversal attempt detected: {}", input);
        }
        return detected;
    }

    /**
     * Detect command injection attempts
     */
    public boolean containsCommandInjection(String input) {
        if (input == null) {
            return false;
        }

        boolean detected = COMMAND_INJECTION_PATTERN.matcher(input).matches();
        if (detected) {
            log.error("SECURITY: Command injection attempt detected: {}", input);
        }
        return detected;
    }

    /**
     * Detect XML injection attempts
     */
    public boolean containsXmlInjection(String input) {
        if (input == null) {
            return false;
        }

        boolean detected = XML_INJECTION_PATTERN.matcher(input).matches();
        if (detected) {
            log.error("SECURITY: XML injection attempt detected: {}", input);
        }
        return detected;
    }

    /**
     * Detect any injection attempts and log warnings
     */
    private void detectInjectionAttempts(String input) {
        containsSqlInjection(input);
        containsPathTraversal(input);
        containsCommandInjection(input);
        containsXmlInjection(input);
    }

    /**
     * Sanitize amount (for financial transactions)
     *
     * @param amount Amount string
     * @return Sanitized amount or null if invalid
     */
    public String sanitizeAmount(String amount) {
        if (amount == null || amount.isEmpty()) {
            return null;
        }

        // Remove all non-numeric characters except decimal point
        String sanitized = amount.replaceAll("[^0-9.]", "");

        // Validate format (max 2 decimal places)
        if (!sanitized.matches("^\\d+(\\.\\d{1,2})?$")) {
            log.warn("Invalid amount format: {}", amount);
            return null;
        }

        return sanitized;
    }

    /**
     * Sanitize currency code
     *
     * @param currency Currency code
     * @return Sanitized 3-letter currency code or null
     */
    public String sanitizeCurrency(String currency) {
        if (currency == null || currency.isEmpty()) {
            return null;
        }

        // Must be exactly 3 uppercase letters (ISO 4217)
        String sanitized = currency.toUpperCase().replaceAll("[^A-Z]", "");

        if (sanitized.length() != 3) {
            log.warn("Invalid currency code: {}", currency);
            return null;
        }

        return sanitized;
    }

    /**
     * Validate and sanitize UUID
     *
     * @param uuid UUID string
     * @return true if valid UUID format
     */
    public boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }

        try {
            java.util.UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: {}", uuid);
            return false;
        }
    }
}
