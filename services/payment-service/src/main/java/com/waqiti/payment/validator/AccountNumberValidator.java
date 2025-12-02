package com.waqiti.payment.validator;

/**
 * Utility class for validating and formatting account numbers
 */
public class AccountNumberValidator {

    private AccountNumberValidator() {
        // Utility class
    }

    /**
     * Get last 4 digits of account number
     */
    public static String getLastFour(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * Validate account number format
     */
    public static boolean isValid(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }

        // Account numbers typically 4-17 digits
        String cleaned = accountNumber.replaceAll("[^0-9]", "");
        return cleaned.length() >= 4 && cleaned.length() <= 17;
    }

    /**
     * Mask account number for display (show only last 4)
     */
    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        String lastFour = getLastFour(accountNumber);
        return "****" + lastFour;
    }
}
