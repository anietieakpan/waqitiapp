package com.waqiti.payment.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for validating bank account numbers
 */
@UtilityClass
@Slf4j
public class AccountNumberValidator {
    
    private static final int MIN_ACCOUNT_LENGTH = 4;
    private static final int MAX_ACCOUNT_LENGTH = 17;
    
    /**
     * Validates a bank account number
     * 
     * @param accountNumber The account number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }
        
        // Remove any non-alphanumeric characters (some banks use letters)
        String cleaned = accountNumber.replaceAll("[^a-zA-Z0-9]", "");
        
        // Check length constraints
        if (cleaned.length() < MIN_ACCOUNT_LENGTH || cleaned.length() > MAX_ACCOUNT_LENGTH) {
            return false;
        }
        
        if (!cleaned.chars().allMatch(Character::isLetterOrDigit)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Masks an account number for display (shows only last 4 digits)
     * 
     * @param accountNumber The account number to mask
     * @return Masked account number
     */
    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        
        String cleaned = accountNumber.replaceAll("[^a-zA-Z0-9]", "");
        if (cleaned.length() >= 4) {
            int maskLength = cleaned.length() - 4;
            return "*".repeat(maskLength) + cleaned.substring(cleaned.length() - 4);
        }
        
        return "****";
    }
    
    /**
     * Gets the last 4 digits of an account number
     * 
     * @param accountNumber The account number
     * @return Last 4 digits or the full number if less than 4 digits
     */
    public static String getLastFour(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return "";
        }
        
        String cleaned = accountNumber.replaceAll("[^a-zA-Z0-9]", "");
        if (cleaned.length() >= 4) {
            return cleaned.substring(cleaned.length() - 4);
        }
        
        return cleaned;
    }
    
    /**
     * Sanitizes an account number by removing invalid characters
     * 
     * @param accountNumber The account number to sanitize
     * @return Sanitized account number
     */
    /**
     * SECURITY FIX: Replace null return with empty string to prevent NullPointerException
     * This ensures consistent behavior for downstream processing
     */
    public static String sanitize(String accountNumber) {
        if (accountNumber == null) {
            log.warn("Account number sanitization requested but input is null");
            return "";
        }
        
        try {
            // Remove all non-alphanumeric characters
            String sanitized = accountNumber.replaceAll("[^a-zA-Z0-9]", "");
            
            // Log if input was significantly modified (potential security concern)
            if (sanitized.length() < accountNumber.length() * 0.8) {
                log.warn("Account number contained {} non-alphanumeric characters", 
                        accountNumber.length() - sanitized.length());
            }
            
            return sanitized;
        } catch (Exception e) {
            log.error("Failed to sanitize account number", e);
            return "";
        }
    }
    
    /**
     * Checks if the account number appears to be a checking account
     * This is a heuristic and may not be accurate for all banks
     * 
     * @param accountNumber The account number to check
     * @return true if likely a checking account
     */
    public static boolean isLikelyCheckingAccount(String accountNumber) {
        if (!isValid(accountNumber)) {
            return false;
        }
        
        String cleaned = sanitize(accountNumber);
        
        if (cleaned.length() >= 2) {
            String prefix = cleaned.substring(0, 2);
            return prefix.matches("[0-9]{2}") && !prefix.startsWith("9");
        }
        
        return cleaned.matches("\\d+");
    }
    
    /**
     * Validates if two account numbers match (ignoring formatting)
     * 
     * @param accountNumber1 First account number
     * @param accountNumber2 Second account number
     * @return true if they match
     */
    public static boolean matches(String accountNumber1, String accountNumber2) {
        if (accountNumber1 == null || accountNumber2 == null) {
            return false;
        }
        
        String cleaned1 = sanitize(accountNumber1);
        String cleaned2 = sanitize(accountNumber2);
        
        return cleaned1.equals(cleaned2);
    }
}