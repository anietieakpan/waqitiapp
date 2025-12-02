package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidPaymentAmount;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * Validator for payment amounts
 */
public class PaymentAmountValidator implements ConstraintValidator<ValidPaymentAmount, Number> {
    
    private double min;
    private double max;
    
    @Override
    public void initialize(ValidPaymentAmount constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }
    
    @Override
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        
        double amount = value.doubleValue();
        
        // Check range
        if (amount < min || amount > max) {
            return false;
        }
        
        // Check for reasonable decimal places (max 2 for most currencies)
        if (value instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) value;
            return bd.scale() <= 2;
        }
        
        return true;
    }
}