package com.waqiti.common.util;

import java.security.SecureRandom;

/**
 * Utilities for string operations
 */
public class StringUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a random alphanumeric string
     */
    public static String generateRandomString(int length) {
        String alphaNumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz";
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(alphaNumeric.charAt(RANDOM.nextInt(alphaNumeric.length())));
        }

        return sb.toString();
    }

    /**
     * Generates a reference number with prefix
     */
    public static String generateReferenceNumber(String prefix) {
        return prefix + System.currentTimeMillis() + generateRandomString(6);
    }

    /**
     * Masks a string for security (e.g., credit card, phone)
     */
    public static String maskString(String input, int visibleStart, int visibleEnd) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        int length = input.length();

        if (length <= visibleStart + visibleEnd) {
            return input;
        }

        StringBuilder masked = new StringBuilder();

        for (int i = 0; i < length; i++) {
            if (i < visibleStart || i >= length - visibleEnd) {
                masked.append(input.charAt(i));
            } else {
                masked.append("*");
            }
        }

        return masked.toString();
    }
}