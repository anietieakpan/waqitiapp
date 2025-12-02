package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for bank routing numbers (ABA routing numbers)
 */
public class RoutingNumberValidator implements ConstraintValidator<ValidationConstraints.ValidRoutingNumber, String> {
    
    @Override
    public void initialize(ValidationConstraints.ValidRoutingNumber constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull/@NotEmpty handle null/empty validation
        }
        
        // Remove any non-digit characters
        String routingNumber = value.replaceAll("[^0-9]", "");
        
        // Must be exactly 9 digits
        if (routingNumber.length() != 9) {
            return false;
        }
        
        // Check if all characters are digits
        if (!routingNumber.matches("\\d{9}")) {
            return false;
        }
        
        // Validate using ABA checksum algorithm
        return validateABAChecksum(routingNumber);
    }
    
    /**
     * Validates routing number using ABA checksum algorithm
     * The checksum is calculated as:
     * (3 * (d1 + d4 + d7) + 7 * (d2 + d5 + d8) + (d3 + d6 + d9)) mod 10 = 0
     */
    private boolean validateABAChecksum(String routingNumber) {
        int[] digits = new int[9];
        for (int i = 0; i < 9; i++) {
            digits[i] = Character.getNumericValue(routingNumber.charAt(i));
        }
        
        int checksum = 3 * (digits[0] + digits[3] + digits[6]) +
                      7 * (digits[1] + digits[4] + digits[7]) +
                      (digits[2] + digits[5] + digits[8]);
        
        return checksum % 10 == 0;
    }
}