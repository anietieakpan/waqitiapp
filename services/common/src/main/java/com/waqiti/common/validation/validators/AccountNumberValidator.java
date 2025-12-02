package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidAccountNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for bank account numbers
 */
public class AccountNumberValidator implements ConstraintValidator<ValidAccountNumber, String> {
    
    @Override
    public void initialize(ValidAccountNumber constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Basic validation: alphanumeric, 4-17 characters
        return value.matches("^[A-Za-z0-9]{4,17}$");
    }
}