package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;

/**
 * Validator for currency amount validation
 */
public class CurrencyAmountValidator implements ConstraintValidator<ValidationConstraints.ValidCurrencyAmount, Object> {
    
    private String currency;
    private double min;
    private double max;
    private int scale;
    
    private static final Set<String> VALID_CURRENCIES = Set.of(
        "USD", "EUR", "GBP", "JPY", "CNY", "AUD", "CAD", "CHF", "SEK", "NZD",
        "NGN", "ZAR", "KES", "GHS", "EGP", "MAD", "TND", "ETB", "UGX", "TZS"
    );
    
    @Override
    public void initialize(ValidationConstraints.ValidCurrencyAmount constraintAnnotation) {
        this.currency = constraintAnnotation.currency();
        this.min = Double.parseDouble(constraintAnnotation.min());
        this.max = Double.parseDouble(constraintAnnotation.max());
        this.scale = constraintAnnotation.scale();
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        
        // Handle composite object with amount and currency fields
        if (value instanceof CurrencyAmountHolder) {
            CurrencyAmountHolder holder = (CurrencyAmountHolder) value;
            return validateAmount(holder.getAmount()) && validateCurrency(holder.getCurrency());
        }
        
        // Handle BigDecimal amount directly (currency specified in annotation)
        if (value instanceof BigDecimal) {
            BigDecimal amount = (BigDecimal) value;
            return validateAmount(amount) && validateCurrency(currency);
        }
        
        // Handle String amount
        if (value instanceof String) {
            try {
                BigDecimal amount = new BigDecimal((String) value);
                return validateAmount(amount) && validateCurrency(currency);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return false;
    }
    
    private boolean validateAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        
        // Check minimum
        if (amount.compareTo(BigDecimal.valueOf(min)) < 0) {
            return false;
        }
        
        // Check maximum
        if (max > 0 && amount.compareTo(BigDecimal.valueOf(max)) > 0) {
            return false;
        }
        
        // Check scale (decimal places)
        if (scale >= 0 && amount.scale() > scale) {
            return false;
        }
        
        return true;
    }
    
    private boolean validateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty()) {
            return false;
        }
        
        // Check if it's a valid ISO currency code
        if (!VALID_CURRENCIES.contains(currencyCode.toUpperCase())) {
            try {
                Currency.getInstance(currencyCode);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Interface for objects that hold currency amount data
     */
    public interface CurrencyAmountHolder {
        BigDecimal getAmount();
        String getCurrency();
    }
}