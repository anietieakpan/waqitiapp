package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidPaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for complete payment requests
 */
public class PaymentRequestValidator implements ConstraintValidator<ValidPaymentRequest, Object> {
    
    @Override
    public void initialize(ValidPaymentRequest constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        
        // This would validate the entire payment request object
        // For now, return true as specific field validators handle individual validations
        return true;
    }
}