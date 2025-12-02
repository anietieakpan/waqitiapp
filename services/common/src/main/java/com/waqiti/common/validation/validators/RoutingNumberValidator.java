package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidRoutingNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for bank routing numbers
 */
public class RoutingNumberValidator implements ConstraintValidator<ValidRoutingNumber, String> {
    
    @Override
    public void initialize(ValidRoutingNumber constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // US routing number validation (9 digits with checksum)
        if (value.length() != 9 || !value.matches("\\d{9}")) {
            return false;
        }
        
        // Validate checksum using ABA algorithm
        int checksum = 0;
        for (int i = 0; i < 9; i += 3) {
            checksum += Character.getNumericValue(value.charAt(i)) * 3;
            checksum += Character.getNumericValue(value.charAt(i + 1)) * 7;
            checksum += Character.getNumericValue(value.charAt(i + 2));
        }
        
        return checksum % 10 == 0;
    }
}