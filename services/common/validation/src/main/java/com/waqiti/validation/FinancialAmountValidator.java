package com.waqiti.validation;

import com.waqiti.validation.annotation.ValidFinancialAmount;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Financial Amount Validator
 * 
 * Comprehensive validation for financial amounts including:
 * - Null and zero value checks
 * - Maximum and minimum amount limits
 * - Decimal precision validation
 * - Overflow protection
 * - Currency-specific rules
 * - Negative value prevention
 * 
 * SECURITY: Prevents integer overflow, precision loss, and invalid financial amounts
 */
@Slf4j
public class FinancialAmountValidator implements ConstraintValidator<ValidFinancialAmount, Object> {
    
    // Maximum allowed amount to prevent overflow (equivalent to $999,999,999.9999)
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.9999");
    
    // Minimum allowed amount (0.0001 for micro-transactions)
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.0001");
    
    // Maximum decimal places allowed
    private static final int MAX_DECIMAL_PLACES = 4;
    
    // Pattern for valid amount format
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,4})?$");
    
    private boolean allowZero;
    private boolean allowNegative;
    private String currency;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private int maxDecimalPlaces;
    
    @Override
    public void initialize(ValidFinancialAmount annotation) {
        this.allowZero = annotation.allowZero();
        this.allowNegative = annotation.allowNegative();
        this.currency = annotation.currency();
        this.maxDecimalPlaces = annotation.maxDecimalPlaces() > 0 ? 
            annotation.maxDecimalPlaces() : MAX_DECIMAL_PLACES;
        
        // Set max amount based on annotation or use default
        this.maxAmount = !annotation.maxAmount().equals("") ? 
            new BigDecimal(annotation.maxAmount()) : MAX_AMOUNT;
            
        // Set min amount based on annotation or use default
        this.minAmount = !annotation.minAmount().equals("") ? 
            new BigDecimal(annotation.minAmount()) : MIN_AMOUNT;
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            addViolation(context, "Amount cannot be null");
            return false;
        }
        
        BigDecimal amount;
        
        try {
            // Convert to BigDecimal
            if (value instanceof String) {
                amount = parseStringAmount((String) value, context);
            } else if (value instanceof BigDecimal) {
                amount = (BigDecimal) value;
            } else if (value instanceof Number) {
                amount = new BigDecimal(value.toString());
            } else {
                addViolation(context, "Amount must be a number or string");
                return false;
            }
            
            if (amount == null) {
                return false; // Error already added in parseStringAmount
            }
            
        } catch (NumberFormatException e) {
            addViolation(context, "Invalid number format");
            return false;
        }
        
        // Validate the amount
        return validateAmount(amount, context);
    }
    
    /**
     * Parse string amount with validation
     */
    private BigDecimal parseStringAmount(String amountStr, ConstraintValidatorContext context) {
        if (amountStr.trim().isEmpty()) {
            addViolation(context, "Amount cannot be empty");
            return null;
        }
        
        // Remove common formatting characters
        String cleanAmount = amountStr.trim()
            .replace(",", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "");
        
        // Check format
        if (!AMOUNT_PATTERN.matcher(cleanAmount).matches()) {
            addViolation(context, "Invalid amount format");
            return null;
        }
        
        try {
            return new BigDecimal(cleanAmount);
        } catch (NumberFormatException e) {
            addViolation(context, "Invalid number format: " + cleanAmount);
            return null;
        }
    }
    
    /**
     * Comprehensive amount validation
     */
    private boolean validateAmount(BigDecimal amount, ConstraintValidatorContext context) {
        boolean isValid;
        
        // Check for zero
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            if (!allowZero) {
                addViolation(context, "Amount cannot be zero");
                isValid = false;
            }
            return isValid; // Zero is valid if allowed, skip other checks
        }
        
        // Check for negative values
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            if (!allowNegative) {
                addViolation(context, "Amount cannot be negative");
                isValid = false;
            }
            // Convert to absolute value for remaining validations
            amount = amount.abs();
        }
        
        // Check minimum amount
        if (amount.compareTo(minAmount) < 0) {
            addViolation(context, 
                String.format("Amount must be at least %s", minAmount));
            isValid = false;
        }
        
        // Check maximum amount (overflow protection)
        if (amount.compareTo(maxAmount) > 0) {
            addViolation(context, 
                String.format("Amount cannot exceed %s", maxAmount));
            isValid = false;
        }
        
        // Check decimal places
        if (amount.scale() > maxDecimalPlaces) {
            addViolation(context, 
                String.format("Amount cannot have more than %d decimal places", maxDecimalPlaces));
            isValid = false;
        }
        
        // Check precision (total digits)
        if (amount.precision() > 15) { // Maximum safe precision
            addViolation(context, "Amount has too many digits");
            isValid = false;
        }
        
        // Currency-specific validation
        if (!currency.isEmpty()) {
            if (!validateCurrencySpecificRules(amount, currency, context)) {
                isValid = false;
            }
        }
        
        // Log validation attempt for security monitoring
        if (!isValid) {
            log.warn("SECURITY: Invalid financial amount validation attempt: {}", amount);
        }
        
        return isValid;
    }
    
    /**
     * Currency-specific validation rules
     */
    private boolean validateCurrencySpecificRules(BigDecimal amount, String currency, 
                                                 ConstraintValidatorContext context) {
        
        return switch (currency.toUpperCase()) {
            case "USD", "EUR", "GBP", "CAD", "AUD" -> {
                // Standard fiat currencies - 2 decimal places max in practice
                if (amount.scale() > 2) {
                    addViolation(context, 
                        String.format("%s amounts should not have more than 2 decimal places", currency));
                    yield false;
                }
                yield true;
            }
            case "JPY", "KRW" -> {
                // Currencies without decimal subdivisions
                if (amount.scale() > 0) {
                    addViolation(context, 
                        String.format("%s amounts should not have decimal places", currency));
                    yield false;
                }
                yield true;
            }
            case "BTC", "ETH" -> {
                // Cryptocurrencies - allow more precision
                if (amount.scale() > 8) {
                    addViolation(context, 
                        String.format("%s amounts cannot have more than 8 decimal places", currency));
                    yield false;
                }
                yield true;
            }
            default -> {
                // Unknown currency - use default validation
                log.debug("Unknown currency for validation: {}", currency);
                yield true;
            }
        };
    }
    
    /**
     * Add validation violation message
     */
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    /**
     * Utility method to normalize amount for storage
     * Ensures consistent decimal places and rounding
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        
        int scale = switch (currency.toUpperCase()) {
            case "JPY", "KRW" -> 0;
            case "BTC", "ETH" -> 8;
            default -> 4; // Default to 4 decimal places
        };
        
        return amount.setScale(scale, RoundingMode.HALF_EVEN);
    }
    
    /**
     * Check if amount is within safe processing limits
     * Used for additional runtime checks
     */
    public static boolean isSafeAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        
        BigDecimal absAmount = amount.abs();
        
        return absAmount.compareTo(MAX_AMOUNT) <= 0 && 
               absAmount.compareTo(MIN_AMOUNT) >= 0 &&
               absAmount.precision() <= 15;
    }
    
    /**
     * Validate amount for specific transaction types
     */
    public static ValidationResult validateForTransactionType(
            BigDecimal amount, TransactionType type, String currency) {
        
        ValidationResult result = new ValidationResult();
        
        if (amount == null) {
            result.addError("Amount cannot be null");
            return result;
        }
        
        // Type-specific validation
        switch (type) {
            case PAYMENT, TRANSFER -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    result.addError("Payment amount must be positive");
                }
                if (amount.compareTo(new BigDecimal("50000")) > 0) {
                    result.addWarning("Large payment amount - may require additional verification");
                }
            }
            case REFUND -> {
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError("Refund amount cannot be negative");
                }
            }
            case FEE -> {
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError("Fee amount cannot be negative");
                }
                if (amount.compareTo(new BigDecimal("1000")) > 0) {
                    result.addWarning("Unusually high fee amount");
                }
            }
        }
        
        return result;
    }
    
    /**
     * Transaction types for validation
     */
    public enum TransactionType {
        PAYMENT,
        TRANSFER, 
        REFUND,
        FEE,
        WITHDRAWAL,
        DEPOSIT
    }
    
    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}