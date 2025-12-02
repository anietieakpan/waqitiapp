package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidRecurringFrequency;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for recurring payment frequencies
 */
public class RecurringFrequencyValidator implements ConstraintValidator<ValidRecurringFrequency, String> {
    
    @Override
    public void initialize(ValidRecurringFrequency constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Valid frequencies
        return value.matches("^(DAILY|WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|ANNUALLY)$");
    }
}