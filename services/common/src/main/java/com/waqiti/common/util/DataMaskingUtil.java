package com.waqiti.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Data Masking Utility for PCI DSS and GDPR Compliance
 *
 * Provides secure masking methods for sensitive data in logs, audit trails, and user interfaces.
 * All methods are null-safe and handle edge cases gracefully.
 *
 * Compliance Standards:
 * - PCI DSS v4.0: Requirement 3.3, 3.4 (Cardholder data masking)
 * - GDPR Article 32: Personal data protection in processing
 * - SOC 2 Type II: Logging and monitoring controls
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@Slf4j
@UtilityClass
public class DataMaskingUtil {

    private static final String MASK_CHAR = "*";
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.{1,3})(.*)(@.*)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+?\\d{1,3})?(.*)(.{4})$");

    /**
     * Mask email address - shows first 3 chars and domain
     *
     * Examples:
     * - user@example.com → use***@example.com
     * - ab@test.com → ab*@test.com
     * - verylongemail@domain.com → ver***@domain.com
     *
     * @param email Email address to mask
     * @return Masked email or "[REDACTED]" if invalid
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return REDACTED;
        }

        try {
            var matcher = EMAIL_PATTERN.matcher(email.trim());
            if (!matcher.matches()) {
                return REDACTED;
            }

            String prefix = matcher.group(1);
            String middle = matcher.group(2);
            String domain = matcher.group(3);

            if (middle.length() <= 2) {
                return prefix + MASK_CHAR + domain;
            }

            return prefix + MASK_CHAR.repeat(3) + domain;

        } catch (Exception e) {
            log.warn("Failed to mask email, returning redacted: {}", e.getMessage());
            return REDACTED;
        }
    }

    /**
     * Mask credit card number - PCI DSS compliant (shows last 4 digits only)
     *
     * Examples:
     * - 4532015112830366 → ************0366
     * - 5425233430109903 → ************9903
     *
     * PCI DSS Requirement 3.3: Mask PAN when displayed (minimum first 6 and last 4 digits)
     * We use a more conservative approach: show ONLY last 4 digits
     *
     * @param cardNumber Credit card number to mask
     * @return Masked card number or "[REDACTED]"
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return REDACTED;
        }

        // Remove spaces and dashes
        String cleaned = cardNumber.replaceAll("[\\s-]", "");

        if (cleaned.length() < 4) {
            return MASK_CHAR.repeat(4);
        }

        if (cleaned.length() < 13 || cleaned.length() > 19) {
            log.warn("Invalid card number length: {}, masking fully", cleaned.length());
            return MASK_CHAR.repeat(16);
        }

        String lastFour = cleaned.substring(cleaned.length() - 4);
        return MASK_CHAR.repeat(12) + lastFour;
    }

    /**
     * Mask CVV/CVC - NEVER log actual CVV (PCI DSS Requirement 3.2)
     *
     * @param cvv CVV code
     * @return Always returns "***" regardless of input
     */
    public static String maskCVV(String cvv) {
        return "***";
    }

    /**
     * Mask IBAN - shows first 4 chars (country + check) and last 4 chars
     *
     * Examples:
     * - GB82WEST12345698765432 → GB82************5432
     * - DE89370400440532013000 → DE89************3000
     *
     * @param iban IBAN to mask
     * @return Masked IBAN or "[REDACTED]"
     */
    public static String maskIBAN(String iban) {
        if (iban == null || iban.isBlank()) {
            return REDACTED;
        }

        String cleaned = iban.replaceAll("\\s", "").trim().toUpperCase();

        if (cleaned.length() < 8) {
            return REDACTED;
        }

        // IBAN format: CC12XXXXXXXXXXXX1234 (Country + Check + Bank + Account)
        String prefix = cleaned.substring(0, 4);
        String suffix = cleaned.substring(cleaned.length() - 4);
        int middleLength = cleaned.length() - 8;

        return prefix + MASK_CHAR.repeat(middleLength) + suffix;
    }

    /**
     * Mask account number - shows last 4 digits only
     *
     * Examples:
     * - 123456789012 → ********9012
     * - ACC-98765432 → ********5432
     *
     * @param accountNumber Account number to mask
     * @return Masked account number or "[REDACTED]"
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return REDACTED;
        }

        String cleaned = accountNumber.replaceAll("[^0-9]", "");

        if (cleaned.length() < 4) {
            return MASK_CHAR.repeat(4);
        }

        String lastFour = cleaned.substring(cleaned.length() - 4);
        return MASK_CHAR.repeat(Math.min(cleaned.length() - 4, 8)) + lastFour;
    }

    /**
     * Mask phone number - shows country code and last 4 digits
     *
     * Examples:
     * - +1234567890 → +1******7890
     * - 555-123-4567 → ****4567
     * - +44 20 7123 4567 → +44******4567
     *
     * @param phoneNumber Phone number to mask
     * @return Masked phone number or "[REDACTED]"
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return REDACTED;
        }

        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        if (cleaned.length() < 4) {
            return MASK_CHAR.repeat(4);
        }

        try {
            var matcher = PHONE_PATTERN.matcher(cleaned);
            if (!matcher.matches()) {
                // Fallback: mask all but last 4
                String lastFour = cleaned.substring(cleaned.length() - 4);
                return MASK_CHAR.repeat(4) + lastFour;
            }

            String countryCode = matcher.group(1) != null ? matcher.group(1) : "";
            String lastFour = matcher.group(3);
            int middleLength = Math.max(cleaned.length() - countryCode.length() - 4, 0);

            return countryCode + MASK_CHAR.repeat(Math.max(middleLength, 4)) + lastFour;

        } catch (Exception e) {
            log.warn("Failed to mask phone number, returning redacted: {}", e.getMessage());
            return REDACTED;
        }
    }

    /**
     * Mask SSN/National ID - shows last 4 digits only
     *
     * Examples:
     * - 123-45-6789 → ***-**-6789
     * - 123456789 → *****6789
     *
     * @param ssn SSN or national ID to mask
     * @return Masked SSN or "[REDACTED]"
     */
    public static String maskSSN(String ssn) {
        if (ssn == null || ssn.isBlank()) {
            return REDACTED;
        }

        String cleaned = ssn.replaceAll("[^0-9]", "");

        if (cleaned.length() < 4) {
            return MASK_CHAR.repeat(cleaned.length());
        }

        if (cleaned.length() == 9) {
            // US SSN format: XXX-XX-1234
            String lastFour = cleaned.substring(5);
            return "***-**-" + lastFour;
        }

        // Generic format
        String lastFour = cleaned.substring(cleaned.length() - 4);
        return MASK_CHAR.repeat(cleaned.length() - 4) + lastFour;
    }

    /**
     * Mask API key - shows first 8 chars and last 4 chars
     *
     * Examples:
     * - sk_live_51H7... → sk_live_****************7ABC
     * - pk_test_TYooM... → pk_test_****************MQ9X
     *
     * @param apiKey API key to mask
     * @return Masked API key or "[REDACTED]"
     */
    public static String maskAPIKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return REDACTED;
        }

        if (apiKey.length() < 12) {
            return MASK_CHAR.repeat(8) + "****";
        }

        String prefix = apiKey.substring(0, 8);
        String suffix = apiKey.substring(apiKey.length() - 4);
        int middleLength = apiKey.length() - 12;

        return prefix + MASK_CHAR.repeat(Math.max(middleLength, 8)) + suffix;
    }

    /**
     * Mask password/token - NEVER show any part of password
     *
     * @param password Password or token
     * @return Always returns "[REDACTED]"
     */
    public static String maskPassword(String password) {
        return REDACTED;
    }

    /**
     * Mask IP address - shows first octet and last octet
     *
     * Examples:
     * - 192.168.1.100 → 192.*.***.100
     * - 10.0.0.1 → 10.*.*.1
     *
     * @param ipAddress IP address to mask
     * @return Masked IP or "[REDACTED]"
     */
    public static String maskIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return REDACTED;
        }

        String[] parts = ipAddress.trim().split("\\.");

        if (parts.length != 4) {
            // Could be IPv6 or invalid, redact fully
            return REDACTED;
        }

        return parts[0] + ".*.*." + parts[3];
    }

    /**
     * Partially mask a generic string - shows first 25% and last 25%
     *
     * Use this for non-standard sensitive data that doesn't fit other categories
     *
     * @param data Data to mask
     * @param showChars Number of characters to show at start and end
     * @return Masked string or "[REDACTED]"
     */
    public static String maskGeneric(String data, int showChars) {
        if (data == null || data.isBlank()) {
            return REDACTED;
        }

        if (showChars < 0) {
            showChars = 0;
        }

        if (data.length() <= showChars * 2) {
            return MASK_CHAR.repeat(data.length());
        }

        String prefix = data.substring(0, showChars);
        String suffix = data.substring(data.length() - showChars);
        int middleLength = data.length() - (showChars * 2);

        return prefix + MASK_CHAR.repeat(middleLength) + suffix;
    }

    /**
     * Check if a string contains potential sensitive data patterns
     *
     * @param text Text to check
     * @return true if text might contain sensitive data
     */
    public static boolean containsSensitiveData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String lower = text.toLowerCase();

        // Check for common sensitive data indicators
        return lower.contains("password") ||
               lower.contains("token") ||
               lower.contains("secret") ||
               lower.contains("key") ||
               lower.contains("cvv") ||
               lower.contains("cvc") ||
               lower.contains("ssn") ||
               lower.matches(".*\\b\\d{13,19}\\b.*") || // Potential card number
               lower.matches(".*\\b\\d{3}-\\d{2}-\\d{4}\\b.*"); // Potential SSN
    }
}
