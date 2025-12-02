package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidCVV;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for credit card CVV codes
 */
public class CVVValidator implements ConstraintValidator<ValidCVV, String> {
    
    @Override
    public void initialize(ValidCVV constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // CVV should be 3 or 4 digits
        return value.matches("^\\d{3,4}$");
    }
}