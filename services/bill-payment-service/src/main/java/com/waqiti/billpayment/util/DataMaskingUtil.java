package com.waqiti.billpayment.util;

import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data in logs
 * Protects PII (Personally Identifiable Information) and financial data
 */
public class DataMaskingUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\b\\d{8,17}\\b");

    /**
     * Mask email addresses
     * Example: john.doe@example.com → j***@example.com
     */
    public static String maskEmail(String text) {
        if (text == null) return null;
        return EMAIL_PATTERN.matcher(text).replaceAll(matchResult -> {
            String username = matchResult.group(1);
            String domain = matchResult.group(2);
            return username.charAt(0) + "***@" + domain;
        });
    }

    /**
     * Mask phone numbers
     * Example: 555-123-4567 → ***-***-4567
     */
    public static String maskPhone(String text) {
        if (text == null) return null;
        return PHONE_PATTERN.matcher(text).replaceAll("***-***-$1");
    }

    /**
     * Mask credit card numbers
     * Example: 4111-1111-1111-1111 → ****-****-****-1111
     */
    public static String maskCardNumber(String text) {
        if (text == null) return null;
        return CARD_NUMBER_PATTERN.matcher(text).replaceAll(matchResult -> {
            String card = matchResult.group();
            String lastFour = card.replaceAll("[\\s-]", "");
            lastFour = lastFour.substring(lastFour.length() - 4);
            return "****-****-****-" + lastFour;
        });
    }

    /**
     * Mask SSN
     * Example: 123-45-6789 → ***-**-6789
     */
    public static String maskSSN(String text) {
        if (text == null) return null;
        return SSN_PATTERN.matcher(text).replaceAll("***-**-$1");
    }

    /**
     * Mask account numbers (show last 4 digits)
     * Example: 123456789012 → ********9012
     */
    public static String maskAccountNumber(String text) {
        if (text == null) return null;
        return ACCOUNT_NUMBER_PATTERN.matcher(text).replaceAll(matchResult -> {
            String account = matchResult.group();
            if (account.length() < 4) return "****";
            return "****" + account.substring(account.length() - 4);
        });
    }

    /**
     * Mask all sensitive data in text
     * Applies all masking patterns
     */
    public static String maskAll(String text) {
        if (text == null) return null;
        String masked = text;
        masked = maskEmail(masked);
        masked = maskPhone(masked);
        masked = maskCardNumber(masked);
        masked = maskSSN(masked);
        // Account number masking disabled by default as it's too aggressive
        // masked = maskAccountNumber(masked);
        return masked;
    }

    /**
     * Mask user ID for logging
     * Shows first 4 characters for debugging
     */
    public static String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) return "****";
        return userId.substring(0, 4) + "****";
    }

    /**
     * Mask monetary amount for logging
     * Shows currency but masks amount
     */
    public static String maskAmount(String amount) {
        if (amount == null) return null;
        return "***.**";
    }

    /**
     * Mask JSON field values while preserving structure
     * Useful for logging request/response bodies
     */
    public static String maskJsonFields(String json, String... fieldNames) {
        if (json == null) return null;
        String masked = json;
        for (String field : fieldNames) {
            // Match "fieldName": "value" or "fieldName": value
            masked = masked.replaceAll(
                    "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"",
                    "\"" + field + "\": \"***\""
            );
            masked = masked.replaceAll(
                    "\"" + field + "\"\\s*:\\s*([^,}\\]]+)",
                    "\"" + field + "\": \"***\""
            );
        }
        return masked;
    }
}
