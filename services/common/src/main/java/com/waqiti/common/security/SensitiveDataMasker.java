package com.waqiti.common.security;

import lombok.experimental.UtilityClass;
import org.springframework.lang.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data in logs and error messages.
 *
 * <p>This class provides methods to sanitize personally identifiable information (PII)
 * and sensitive security tokens before they are logged, ensuring compliance with:
 * <ul>
 *   <li>PCI DSS Requirement 3.4 - Render PAN unreadable</li>
 *   <li>PCI DSS Requirement 10.3 - Log entry requirements</li>
 *   <li>GDPR Article 32 - Security of processing</li>
 *   <li>CCPA - Protection of consumer personal information</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 * <pre>
 * // Mask session tokens
 * log.info("Processing payment for session: {}", SensitiveDataMasker.maskSessionToken(sessionToken));
 *
 * // Mask email addresses
 * log.info("User registered: {}", SensitiveDataMasker.maskEmail(email));
 *
 * // Mask credit card numbers
 * log.info("Payment method: {}", SensitiveDataMasker.maskCardNumber(cardNumber));
 * </pre>
 *
 * @author Waqiti Security Team
 * @version 1.0
 * @since 2025-10-18
 */
@UtilityClass
public class SensitiveDataMasker {

    private static final String MASKED_VALUE = "***MASKED***";
    private static final String PARTIAL_MASK_CHAR = "*";

    // Regex patterns for various sensitive data types
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[1-9]\\d{1,14}");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\d{13,19}");

    /**
     * Masks a session token by showing only a sanitized identifier.
     *
     * <p>Instead of logging the actual session token (which could be used for session hijacking),
     * this method extracts or generates a non-sensitive session ID for logging purposes.
     *
     * @param sessionToken the session token to mask (may be null)
     * @return a masked session identifier safe for logging
     */
    public static String maskSessionToken(@Nullable String sessionToken) {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return "[NO_SESSION]";
        }

        // Extract session ID if it's a JWT or similar structured token
        if (sessionToken.contains(".")) {
            // Potentially a JWT - extract session ID from claims if possible
            return "[SESSION:" + generateSessionId(sessionToken) + "]";
        }

        // For other token formats, generate a consistent but non-reversible ID
        return "[SESSION:" + generateSessionId(sessionToken) + "]";
    }

    /**
     * Generates a consistent, non-reversible session identifier for logging.
     *
     * <p>This method creates a hash-based identifier that:
     * <ul>
     *   <li>Is consistent for the same session token (aids debugging)</li>
     *   <li>Cannot be reversed to obtain the original token</li>
     *   <li>Is short enough for log readability</li>
     * </ul>
     *
     * @param sessionToken the original session token
     * @return a 12-character hash-based identifier
     */
    private static String generateSessionId(String sessionToken) {
        try {
            int hash = sessionToken.hashCode();
            // Convert to hex and take first 12 characters for readability
            return String.format("%08x", hash).substring(0, 8).toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Masks an email address by showing only the first character and domain.
     *
     * <p>Example: john.doe@example.com → j***@example.com
     *
     * @param email the email address to mask
     * @return masked email address
     */
    public static String maskEmail(@Nullable String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return MASKED_VALUE;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() == 1) {
            return localPart + "***" + domain;
        }

        return localPart.charAt(0) + "***" + domain;
    }

    /**
     * Masks a phone number by showing only the last 4 digits.
     *
     * <p>Example: +1234567890 → ***7890
     *
     * @param phoneNumber the phone number to mask
     * @return masked phone number
     */
    public static String maskPhoneNumber(@Nullable String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "";
        }

        // Remove all non-digit characters except leading +
        String digits = phoneNumber.replaceAll("[^0-9+]", "");

        if (digits.length() <= 4) {
            return PARTIAL_MASK_CHAR.repeat(digits.length());
        }

        return "***" + digits.substring(digits.length() - 4);
    }

    /**
     * Masks a credit card number showing only the last 4 digits (PCI DSS compliant).
     *
     * <p>Example: 4532123456789012 → ****9012
     *
     * @param cardNumber the credit card number to mask
     * @return PCI DSS compliant masked card number
     */
    public static String maskCardNumber(@Nullable String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return "";
        }

        String digits = cardNumber.replaceAll("[^0-9]", "");

        if (digits.length() < 13) {
            return MASKED_VALUE;
        }

        // PCI DSS compliant: show only last 4 digits
        return "****" + digits.substring(digits.length() - 4);
    }

    /**
     * Masks a Social Security Number (SSN).
     *
     * <p>Example: 123-45-6789 → ***-**-6789
     *
     * @param ssn the SSN to mask
     * @return masked SSN
     */
    public static String maskSSN(@Nullable String ssn) {
        if (ssn == null || ssn.isEmpty()) {
            return "";
        }

        if (ssn.matches("\\d{3}-\\d{2}-\\d{4}")) {
            String lastFour = ssn.substring(ssn.length() - 4);
            return "***-**-" + lastFour;
        }

        return MASKED_VALUE;
    }

    /**
     * Masks an API key or secret by showing only a prefix.
     *
     * <p>Example: sk_live_abc123def456 → sk_live_***
     *
     * @param apiKey the API key to mask
     * @return masked API key
     */
    public static String maskApiKey(@Nullable String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }

        int underscoreIndex = apiKey.indexOf('_');
        if (underscoreIndex > 0 && apiKey.length() > underscoreIndex + 1) {
            int secondUnderscore = apiKey.indexOf('_', underscoreIndex + 1);
            if (secondUnderscore > 0) {
                return apiKey.substring(0, secondUnderscore + 1) + "***";
            }
        }

        return apiKey.substring(0, Math.min(8, apiKey.length())) + "***";
    }

    /**
     * Masks a password completely (should never be logged).
     *
     * @param password the password (not used)
     * @return always returns "***REDACTED***"
     */
    public static String maskPassword(@Nullable String password) {
        return "***REDACTED***";
    }

    /**
     * Masks a user ID by converting it to a reference-safe format.
     *
     * <p>UUIDs are generally safe to log, but this method provides consistent formatting.
     *
     * @param userId the user ID
     * @return formatted user ID reference
     */
    public static String formatUserIdForLogging(@Nullable UUID userId) {
        if (userId == null) {
            return "[NO_USER]";
        }
        return "[USER:" + userId + "]";
    }

    /**
     * Masks a string containing sensitive data by replacing any detected patterns.
     *
     * <p>This is a catch-all method that scans for and masks multiple types of sensitive data.
     *
     * @param text the text to sanitize
     * @return sanitized text with sensitive data masked
     */
    public static String maskSensitiveData(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String sanitized = text;

        // Mask emails
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(match -> maskEmail(match.group()));

        // Mask credit card numbers (16 digits)
        sanitized = CARD_PATTERN.matcher(sanitized).replaceAll(match -> maskCardNumber(match.group()));

        // Mask SSNs
        sanitized = SSN_PATTERN.matcher(sanitized).replaceAll(match -> maskSSN(match.group()));

        return sanitized;
    }

    /**
     * Creates a sanitized log context for financial transactions.
     *
     * @param userId the user ID
     * @param transactionId the transaction ID
     * @param amount the transaction amount
     * @param currency the currency code
     * @return sanitized log message
     */
    public static String createTransactionLogContext(UUID userId, UUID transactionId,
                                                     String amount, String currency) {
        return String.format("user=%s, txn=%s, amount=%s %s",
            formatUserIdForLogging(userId),
            transactionId,
            amount,
            currency);
    }
}
