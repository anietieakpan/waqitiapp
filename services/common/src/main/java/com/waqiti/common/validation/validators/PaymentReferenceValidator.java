package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidPaymentReference;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for payment reference numbers
 */
public class PaymentReferenceValidator implements ConstraintValidator<ValidPaymentReference, String> {
    
    @Override
    public void initialize(ValidPaymentReference constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Payment reference should be alphanumeric with optional hyphens
        // Length between 6 and 50 characters
        return value.matches("^[A-Za-z0-9\\-]{6,50}$");
    }
}