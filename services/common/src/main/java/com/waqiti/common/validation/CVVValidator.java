package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for credit card CVV/CVC codes
 */
public class CVVValidator implements ConstraintValidator<ValidationConstraints.CVV, String> {
    
    @Override
    public void initialize(ValidationConstraints.CVV constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull/@NotEmpty handle null/empty validation
        }
        
        String cvv = value.trim();
        
        // CVV must be 3 or 4 digits (4 for American Express)
        if (!cvv.matches("\\d{3,4}")) {
            return false;
        }
        
        // Additional validation rules
        // CVV should not be all zeros or all same digit
        if (cvv.matches("0+") || cvv.matches("(.)\\1+")) {
            return false;
        }
        
        return true;
    }
}