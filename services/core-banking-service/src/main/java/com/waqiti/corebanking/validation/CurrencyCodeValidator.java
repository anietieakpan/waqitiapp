package com.waqiti.corebanking.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Set;

/**
 * Validator implementation for @CurrencyCode annotation
 *
 * Validates currency codes against ISO 4217 standard using Java's Currency class.
 * Supports all standard currency codes available in the JVM.
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private static final Set<String> VALID_CURRENCIES = Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(java.util.stream.Collectors.toSet());

    @Override
    public void initialize(CurrencyCode constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull for null checks
        }

        // Check if it's a valid 3-letter code
        if (value.length() != 3) {
            return false;
        }

        // Check if it's a valid ISO 4217 currency code
        return VALID_CURRENCIES.contains(value.toUpperCase());
    }
}
