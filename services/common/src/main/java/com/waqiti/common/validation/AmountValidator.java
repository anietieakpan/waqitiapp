package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validator implementation for @ValidAmount annotation.
 * Validates monetary amounts for financial transactions.
 */
@Slf4j
public class AmountValidator implements ConstraintValidator<ValidAmount, BigDecimal> {
    
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private boolean allowZero;
    private int maxDecimalPlaces;
    private String currency;
    
    @Override
    public void initialize(ValidAmount constraintAnnotation) {
        this.minAmount = new BigDecimal(constraintAnnotation.min());
        this.maxAmount = new BigDecimal(constraintAnnotation.max());
        this.allowZero = constraintAnnotation.allowZero();
        this.maxDecimalPlaces = constraintAnnotation.maxDecimalPlaces();
        this.currency = constraintAnnotation.currency();
    }
    
    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        // Null values are handled by @NotNull
        if (value == null) {
            return true;
        }
        
        // Check for negative amounts
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            addConstraintViolation(context, "Amount cannot be negative");
            return false;
        }
        
        // Check zero amounts
        if (!allowZero && value.compareTo(BigDecimal.ZERO) == 0) {
            addConstraintViolation(context, "Amount cannot be zero");
            return false;
        }
        
        // Check minimum amount
        if (value.compareTo(minAmount) < 0) {
            addConstraintViolation(context, 
                String.format("Amount must be at least %s", minAmount));
            return false;
        }
        
        // Check maximum amount
        if (value.compareTo(maxAmount) > 0) {
            addConstraintViolation(context, 
                String.format("Amount cannot exceed %s", maxAmount));
            return false;
        }
        
        // Check decimal places
        if (getDecimalPlaces(value) > maxDecimalPlaces) {
            addConstraintViolation(context, 
                String.format("Amount cannot have more than %d decimal places", maxDecimalPlaces));
            return false;
        }
        
        // Currency-specific validation
        if (!currency.isEmpty() && !validateCurrencyAmount(value, currency)) {
            addConstraintViolation(context, 
                String.format("Invalid amount for currency %s", currency));
            return false;
        }
        
        return true;
    }
    
    private int getDecimalPlaces(BigDecimal value) {
        int scale = value.stripTrailingZeros().scale();
        return Math.max(0, scale);
    }
    
    private boolean validateCurrencyAmount(BigDecimal amount, String currencyCode) {
        // Add currency-specific validation rules
        switch (currencyCode.toUpperCase()) {
            case "JPY":
            case "KRW":
                // These currencies don't use decimal places
                return getDecimalPlaces(amount) == 0;
            case "BHD":
            case "KWD":
            case "OMR":
                // These currencies use 3 decimal places
                return getDecimalPlaces(amount) <= 3;
            default:
                // Most currencies use 2 decimal places
                return getDecimalPlaces(amount) <= 2;
        }
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}