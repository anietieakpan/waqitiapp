package com.waqiti.validation;

import com.waqiti.validation.annotation.ValidMoneyAmount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enhanced Money Amount Validator
 * 
 * Comprehensive validation for money amounts including:
 * - Null and zero value checks with configurable rules
 * - Maximum and minimum amount limits per transaction type
 * - Currency-specific decimal precision validation
 * - Overflow and underflow protection
 * - Business rule validation (daily/monthly limits)
 * - Fraud detection thresholds
 * - User tier-based limits
 * 
 * SECURITY: Prevents integer overflow, precision loss, invalid financial amounts,
 * and potential fraud through amount manipulation
 */
@Slf4j
@Component
public class MoneyAmountValidator implements ConstraintValidator<ValidMoneyAmount, Object> {
    
    // Safe limits to prevent overflow and system abuse
    private static final BigDecimal ABSOLUTE_MAX_AMOUNT = new BigDecimal("999999999999.99999999");
    private static final BigDecimal ABSOLUTE_MIN_AMOUNT = new BigDecimal("0.00000001");
    
    // Fraud detection thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal SUSPICIOUS_THRESHOLD = new BigDecimal("50000.00");
    
    // Pattern for valid decimal format
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    
    // Supported fiat currencies with their properties
    private static final Set<String> MAJOR_FIAT_CURRENCIES = Set.of(
        "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "SEK", "NZD"
    );
    
    // Zero-decimal currencies (like JPY, KRW)
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
        "JPY", "KRW", "VND", "CLP", "ISK", "PYG"
    );
    
    // Cryptocurrency codes
    private static final Set<String> CRYPTO_CURRENCIES = Set.of(
        "BTC", "ETH", "LTC", "XRP", "ADA", "DOT", "LINK", "BCH", "XLM", "USDC", "USDT"
    );
    
    private ValidMoneyAmount annotation;
    
    @Override
    public void initialize(ValidMoneyAmount annotation) {
        this.annotation = annotation;
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            addViolation(context, "Amount cannot be null");
            return false;
        }
        
        BigDecimal amount;
        
        try {
            amount = convertToAmount(value, context);
            if (amount == null) {
                return false; // Error already added in conversion
            }
        } catch (Exception e) {
            addViolation(context, "Invalid amount format: " + e.getMessage());
            return false;
        }
        
        return performComprehensiveValidation(amount, context);
    }
    
    /**
     * Convert various input types to BigDecimal
     */
    private BigDecimal convertToAmount(Object value, ConstraintValidatorContext context) {
        try {
            if (value instanceof String str) {
                return parseStringAmount(str, context);
            } else if (value instanceof BigDecimal bd) {
                return bd;
            } else if (value instanceof Number num) {
                return new BigDecimal(num.toString());
            } else {
                addViolation(context, "Amount must be a number, BigDecimal, or string");
                return null;
            }
        } catch (NumberFormatException e) {
            addViolation(context, "Invalid number format");
            return null;
        }
    }
    
    /**
     * Parse string amount with enhanced validation
     */
    private BigDecimal parseStringAmount(String amountStr, ConstraintValidatorContext context) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            addViolation(context, "Amount cannot be empty");
            return null;
        }
        
        // Clean the amount string
        String cleanAmount = cleanAmountString(amountStr);
        
        // Validate format
        if (!DECIMAL_PATTERN.matcher(cleanAmount).matches()) {
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
     * Clean amount string by removing formatting
     */
    private String cleanAmountString(String amountStr) {
        return amountStr.trim()
            .replace(",", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("¥", "")
            .replace("₹", "")
            .replace(" ", "");
    }
    
    /**
     * Comprehensive validation including business rules
     */
    private boolean performComprehensiveValidation(BigDecimal amount, ConstraintValidatorContext context) {
        boolean isValid;
        
        // Basic amount validation
        if (!validateBasicAmount(amount, context)) {
            isValid = false;
        }
        
        // Scale (decimal places) validation
        if (!validateScale(amount, context)) {
            isValid = false;
        }
        
        // Range validation
        if (!validateRange(amount, context)) {
            isValid = false;
        }
        
        // Currency-specific validation
        if (!annotation.currency().isEmpty()) {
            if (!validateCurrencySpecificRules(amount, annotation.currency(), context)) {
                isValid = false;
            }
        }
        
        // Transaction type validation
        if (!validateTransactionType(amount, context)) {
            isValid = false;
        }
        
        // High-value transaction validation
        if (annotation.highValue() || amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            if (!validateHighValueTransaction(amount, context)) {
                isValid = false;
            }
        }
        
        // Fraud detection validation
        if (annotation.checkFraudLimits()) {
            if (!validateFraudLimits(amount, context)) {
                isValid = false;
            }
        }
        
        // User tier validation
        if (!validateUserTierLimits(amount, context)) {
            isValid = false;
        }
        
        // Log validation attempts for security monitoring
        if (!isValid) {
            log.warn("SECURITY: Money amount validation failed for amount: {}, type: {}, currency: {}", 
                amount, annotation.transactionType(), annotation.currency());
        }
        
        return isValid;
    }
    
    /**
     * Basic amount validation (null, zero, negative)
     */
    private boolean validateBasicAmount(BigDecimal amount, ConstraintValidatorContext context) {
        // Check for zero
        if (amount.compareTo(BigDecimal.ZERO) == 0 && !annotation.allowZero()) {
            addViolation(context, "Amount cannot be zero");
            return false;
        }
        
        // Check for negative values
        if (amount.compareTo(BigDecimal.ZERO) < 0 && !annotation.allowNegative()) {
            addViolation(context, "Amount cannot be negative");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate decimal places (scale)
     */
    private boolean validateScale(BigDecimal amount, ConstraintValidatorContext context) {
        int maxScale = annotation.scale();
        
        // Auto-determine scale based on currency if not explicitly set
        if (maxScale == 2 && !annotation.currency().isEmpty()) {
            maxScale = getMaxScaleForCurrency(annotation.currency());
        }
        
        if (amount.scale() > maxScale) {
            addViolation(context, 
                String.format("Amount cannot have more than %d decimal places", maxScale));
            return false;
        }
        
        return true;
    }
    
    /**
     * Get maximum scale for currency
     */
    private int getMaxScaleForCurrency(String currency) {
        String upperCurrency = currency.toUpperCase();
        
        if (ZERO_DECIMAL_CURRENCIES.contains(upperCurrency)) {
            return 0;
        } else if (CRYPTO_CURRENCIES.contains(upperCurrency)) {
            return 8;
        } else {
            return 2; // Default for fiat currencies
        }
    }
    
    /**
     * Validate amount range
     */
    private boolean validateRange(BigDecimal amount, ConstraintValidatorContext context) {
        BigDecimal absAmount = amount.abs();
        BigDecimal minAmount = BigDecimal.valueOf(annotation.min());
        BigDecimal maxAmount = BigDecimal.valueOf(annotation.max());
        
        // Check minimum (only for positive amounts)
        if (amount.compareTo(BigDecimal.ZERO) > 0 && absAmount.compareTo(minAmount) < 0) {
            addViolation(context, 
                String.format("Amount must be at least %s", minAmount));
            return false;
        }
        
        // Check maximum (prevent overflow)
        if (absAmount.compareTo(maxAmount) > 0) {
            addViolation(context, 
                String.format("Amount cannot exceed %s", maxAmount));
            return false;
        }
        
        // Check absolute limits
        if (absAmount.compareTo(ABSOLUTE_MAX_AMOUNT) > 0) {
            addViolation(context, "Amount exceeds system limits");
            return false;
        }
        
        return true;
    }
    
    /**
     * Currency-specific validation rules
     */
    private boolean validateCurrencySpecificRules(BigDecimal amount, String currency, 
                                                 ConstraintValidatorContext context) {
        String upperCurrency = currency.toUpperCase();
        
        // Validate currency is supported
        if (!MAJOR_FIAT_CURRENCIES.contains(upperCurrency) && 
            !CRYPTO_CURRENCIES.contains(upperCurrency)) {
            addViolation(context, "Unsupported currency: " + currency);
            return false;
        }
        
        // Zero-decimal currency validation
        if (ZERO_DECIMAL_CURRENCIES.contains(upperCurrency) && amount.scale() > 0) {
            addViolation(context, 
                String.format("%s amounts should not have decimal places", currency));
            return false;
        }
        
        // Cryptocurrency-specific validation
        if (CRYPTO_CURRENCIES.contains(upperCurrency)) {
            return validateCryptocurrencyAmount(amount, upperCurrency, context);
        }
        
        return true;
    }
    
    /**
     * Validate cryptocurrency amounts
     */
    private boolean validateCryptocurrencyAmount(BigDecimal amount, String currency, 
                                               ConstraintValidatorContext context) {
        // Bitcoin-specific validation
        if ("BTC".equals(currency)) {
            if (amount.compareTo(new BigDecimal("21000000")) > 0) {
                addViolation(context, "BTC amount cannot exceed total supply");
                return false;
            }
            if (amount.scale() > 8) {
                addViolation(context, "BTC amounts cannot have more than 8 decimal places");
                return false;
            }
        }
        
        // Ethereum-specific validation
        if ("ETH".equals(currency)) {
            if (amount.scale() > 18) {
                addViolation(context, "ETH amounts cannot have more than 18 decimal places");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Transaction type-specific validation
     */
    private boolean validateTransactionType(BigDecimal amount, ConstraintValidatorContext context) {
        ValidMoneyAmount.TransactionType type = annotation.transactionType();
        
        return switch (type) {
            case REFUND, REVERSAL, CHARGEBACK -> {
                // These can be negative
                yield true;
            }
            case PAYMENT, TRANSFER, WITHDRAWAL -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    addViolation(context, type + " amount must be positive");
                    yield false;
                }
                yield true;
            }
            case FEE, INTEREST -> {
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    addViolation(context, type + " amount cannot be negative");
                    yield false;
                }
                yield true;
            }
            case CRYPTO_BUY, CRYPTO_SELL, CRYPTO_SWAP -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    addViolation(context, "Cryptocurrency transaction amount must be positive");
                    yield false;
                }
                yield validateCryptoTransactionLimits(amount, context);
            }
            default -> true;
        };
    }
    
    /**
     * Validate cryptocurrency transaction limits
     */
    private boolean validateCryptoTransactionLimits(BigDecimal amount, ConstraintValidatorContext context) {
        // Minimum crypto transaction amount
        if (amount.compareTo(new BigDecimal("0.00000001")) < 0) {
            addViolation(context, "Cryptocurrency amount too small");
            return false;
        }
        
        return true;
    }
    
    /**
     * High-value transaction validation
     */
    private boolean validateHighValueTransaction(BigDecimal amount, ConstraintValidatorContext context) {
        if (amount.compareTo(SUSPICIOUS_THRESHOLD) > 0) {
            log.warn("SECURITY: High-value transaction detected: {}", amount);
            // In a real system, this might trigger additional KYC/AML checks
        }
        
        return true; // Don't block, but log for monitoring
    }
    
    /**
     * Fraud detection limits validation
     */
    private boolean validateFraudLimits(BigDecimal amount, ConstraintValidatorContext context) {
        // This would integrate with a fraud detection service
        // For now, implement basic threshold checks
        
        if (amount.compareTo(new BigDecimal("100000")) > 0) {
            addViolation(context, "Amount exceeds fraud prevention limits");
            return false;
        }
        
        return true;
    }
    
    /**
     * User tier limits validation
     */
    private boolean validateUserTierLimits(BigDecimal amount, ConstraintValidatorContext context) {
        ValidMoneyAmount.UserTier tier = annotation.userTier();
        
        double dailyLimit = tier.getDailyLimit();
        double monthlyLimit = tier.getMonthlyLimit();
        
        if (amount.doubleValue() > dailyLimit) {
            addViolation(context, 
                String.format("Amount exceeds daily limit for %s tier: %s", 
                    tier.name(), dailyLimit));
            return false;
        }
        
        // Note: Monthly limit check would require database lookup in real implementation
        
        return true;
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
     * Utility method to check if amount is safe for processing
     */
    public static boolean isSafeForProcessing(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        
        BigDecimal absAmount = amount.abs();
        return absAmount.compareTo(ABSOLUTE_MAX_AMOUNT) <= 0 && 
               absAmount.compareTo(ABSOLUTE_MIN_AMOUNT) >= 0 &&
               absAmount.precision() <= 20; // Safe precision limit
    }
    
    /**
     * Normalize amount for storage with proper scale
     */
    public static BigDecimal normalizeAmountForCurrency(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        
        int scale = getScaleForCurrency(currency);
        return amount.setScale(scale, RoundingMode.HALF_EVEN);
    }
    
    private static int getScaleForCurrency(String currency) {
        String upperCurrency = currency.toUpperCase();
        
        if (ZERO_DECIMAL_CURRENCIES.contains(upperCurrency)) {
            return 0;
        } else if (CRYPTO_CURRENCIES.contains(upperCurrency)) {
            return "BTC".equals(upperCurrency) ? 8 : 18; // BTC: 8, ETH: 18
        } else {
            return 2; // Standard fiat currency scale
        }
    }
}