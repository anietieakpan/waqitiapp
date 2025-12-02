package com.waqiti.validation.annotation;

import com.waqiti.validation.MoneyAmountValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Enhanced Money Amount Validation Annotation
 * 
 * Comprehensive validation for money amounts with enhanced security and business rules:
 * - Prevents overflow and precision loss
 * - Enforces currency-specific rules and precision
 * - Validates decimal places and format
 * - Configurable min/max limits per transaction type
 * - Fraud detection thresholds
 * - Daily/monthly limits per user tier
 * 
 * SECURITY: Critical for preventing financial fraud, calculation errors,
 * and malicious input that could cause system instability
 * 
 * Usage Examples:
 * 
 * @ValidMoneyAmount
 * private BigDecimal amount;
 * 
 * @ValidMoneyAmount(currency = "USD", max = 10000.00, transactionType = PAYMENT)
 * private BigDecimal paymentAmount;
 * 
 * @ValidMoneyAmount(allowZero = false, allowNegative = false, scale = 2)
 * private BigDecimal transferAmount;
 * 
 * @ValidMoneyAmount(currency = "BTC", scale = 8, max = 100.0)
 * private BigDecimal cryptoAmount;
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MoneyAmountValidator.class)
@Documented
public @interface ValidMoneyAmount {
    
    /**
     * Default error message
     */
    String message() default "Invalid money amount";
    
    /**
     * Validation groups
     */
    Class<?>[] groups() default {};
    
    /**
     * Payload for additional metadata
     */
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Minimum allowed amount
     * Default: 0.01 for fiat currencies
     */
    double min() default 0.01;
    
    /**
     * Maximum allowed amount
     * Default: 1,000,000.00
     */
    double max() default 1_000_000.00;
    
    /**
     * Maximum number of decimal places (scale)
     * Default: 2 for fiat, 8 for crypto
     */
    int scale() default 2;
    
    /**
     * Whether zero values are allowed
     * Default: false
     */
    boolean allowZero() default false;
    
    /**
     * Whether negative values are allowed
     * Default: false (except for refunds/reversals)
     */
    boolean allowNegative() default false;
    
    /**
     * Currency code for currency-specific validation
     * Empty string means use default rules
     */
    String currency() default "";
    
    /**
     * Transaction type for business rule validation
     */
    TransactionType transactionType() default TransactionType.GENERAL;
    
    /**
     * Whether this is a high-value transaction requiring enhanced validation
     */
    boolean highValue() default false;
    
    /**
     * Whether to enforce fraud detection limits
     */
    boolean checkFraudLimits() default true;
    
    /**
     * Whether to validate sufficient balance (if applicable)
     */
    boolean checkBalance() default false;
    
    /**
     * User tier for limit validation
     */
    UserTier userTier() default UserTier.STANDARD;
    
    /**
     * Transaction types for enhanced validation
     */
    enum TransactionType {
        GENERAL,           // Default validation
        PAYMENT,           // Payment transactions
        TRANSFER,          // Money transfers  
        WITHDRAWAL,        // Withdrawals
        DEPOSIT,           // Deposits
        REFUND,            // Refunds (allow negative)
        REVERSAL,          // Transaction reversals (allow negative)
        FEE,              // Fee calculations
        INTEREST,         // Interest calculations
        CRYPTO_BUY,       // Cryptocurrency purchase
        CRYPTO_SELL,      // Cryptocurrency sale
        CRYPTO_SWAP,      // Cryptocurrency exchange
        INTERNATIONAL,    // International transfers
        INVESTMENT_BUY,   // Investment purchase
        INVESTMENT_SELL,  // Investment sale
        LOAN_DISBURSEMENT, // Loan disbursement
        LOAN_REPAYMENT,   // Loan repayment
        CHARGEBACK        // Chargeback (allow negative)
    }
    
    /**
     * User tiers for limit validation
     */
    enum UserTier {
        BASIC(1000.0, 5000.0),        // Basic tier limits
        STANDARD(10000.0, 50000.0),   // Standard tier limits
        PREMIUM(50000.0, 250000.0),   // Premium tier limits
        VIP(100000.0, 500000.0),      // VIP tier limits
        ENTERPRISE(1000000.0, 5000000.0); // Enterprise tier limits
        
        private final double dailyLimit;
        private final double monthlyLimit;
        
        UserTier(double dailyLimit, double monthlyLimit) {
            this.dailyLimit = dailyLimit;
            this.monthlyLimit = monthlyLimit;
        }
        
        public double getDailyLimit() {
            return dailyLimit;
        }
        
        public double getMonthlyLimit() {
            return monthlyLimit;
        }
    }
    
    /**
     * Define multiple @ValidMoneyAmount annotations
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        ValidMoneyAmount[] value();
    }
}