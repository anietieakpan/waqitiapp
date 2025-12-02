package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.PaymentValidation.ValidCurrency;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for currency codes
 */
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
    
    private static final Set<String> VALID_CURRENCIES = Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toSet());
    
    @Override
    public void initialize(ValidCurrency constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        return VALID_CURRENCIES.contains(value.toUpperCase());
    }
}