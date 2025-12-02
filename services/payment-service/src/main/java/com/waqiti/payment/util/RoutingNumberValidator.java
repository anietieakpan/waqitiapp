package com.waqiti.payment.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for validating US bank routing numbers
 * Implements ABA routing number checksum algorithm
 */
@UtilityClass
@Slf4j
public class RoutingNumberValidator {
    
    /**
     * Validates a US bank routing number using the ABA checksum algorithm
     * 
     * @param routingNumber The routing number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String routingNumber) {
        if (routingNumber == null || routingNumber.trim().isEmpty()) {
            return false;
        }
        
        // Remove any non-numeric characters
        String cleaned = routingNumber.replaceAll("[^0-9]", "");
        
        // Routing numbers must be exactly 9 digits
        if (cleaned.length() != 9) {
            return false;
        }
        
        // Validate using ABA checksum algorithm
        try {
            int[] digits = new int[9];
            for (int i = 0; i < 9; i++) {
                digits[i] = Character.getNumericValue(cleaned.charAt(i));
            }
            
            // ABA algorithm: multiply digits by 3, 7, 1, 3, 7, 1, 3, 7
            int checksum = 
                3 * digits[0] +
                7 * digits[1] +
                1 * digits[2] +
                3 * digits[3] +
                7 * digits[4] +
                1 * digits[5] +
                3 * digits[6] +
                7 * digits[7];
            
            // The checksum plus the check digit (last digit) should be divisible by 10
            return (checksum + digits[8]) % 10 == 0;
            
        } catch (Exception e) {
            log.debug("Invalid routing number format: {}", routingNumber, e);
            return false;
        }
    }
    
    /**
     * Formats a routing number for display (adds dashes)
     * 
     * @param routingNumber The routing number to format
     * @return Formatted routing number or original if invalid
     */
    public static String format(String routingNumber) {
        if (!isValid(routingNumber)) {
            return routingNumber;
        }
        
        String cleaned = routingNumber.replaceAll("[^0-9]", "");
        return String.format("%s-%s-%s", 
            cleaned.substring(0, 3),
            cleaned.substring(3, 5),
            cleaned.substring(5, 9)
        );
    }
    
    /**
     * Masks a routing number for display (shows only last 4 digits)
     * 
     * @param routingNumber The routing number to mask
     * @return Masked routing number
     */
    public static String mask(String routingNumber) {
        if (routingNumber == null || routingNumber.length() < 4) {
            return "****";
        }
        
        String cleaned = routingNumber.replaceAll("[^0-9]", "");
        if (cleaned.length() >= 4) {
            return "*****" + cleaned.substring(cleaned.length() - 4);
        }
        
        return "****";
    }
    
    /**
     * Gets the Federal Reserve routing symbol from a routing number
     * First 4 digits identify the Federal Reserve routing symbol
     * 
     * @param routingNumber The routing number
     * @return Federal Reserve routing symbol or null if invalid
     */
    public static String getFederalReserveSymbol(String routingNumber) {
        if (!isValid(routingNumber)) {
            throw new IllegalArgumentException("Invalid routing number provided: " + routingNumber);
        }
        
        String cleaned = routingNumber.replaceAll("[^0-9]", "");
        return cleaned.substring(0, 4);
    }
    
    /**
     * Determines if a routing number is for a Federal Reserve Bank
     * Federal Reserve Banks have routing numbers starting with 00-12
     * 
     * @param routingNumber The routing number to check
     * @return true if Federal Reserve Bank, false otherwise
     */
    public static boolean isFederalReserveBank(String routingNumber) {
        if (!isValid(routingNumber)) {
            return false;
        }
        
        String cleaned = routingNumber.replaceAll("[^0-9]", "");
        int firstTwo = Integer.parseInt(cleaned.substring(0, 2));
        return firstTwo >= 0 && firstTwo <= 12;
    }
}