package com.waqiti.validation.annotation;

import com.waqiti.validation.FinancialAmountValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Financial Amount Validation Annotation
 * 
 * Validates financial amounts with comprehensive security checks:
 * - Prevents overflow and precision loss
 * - Enforces currency-specific rules
 * - Validates decimal places and format
 * - Configurable min/max limits
 * 
 * SECURITY: Critical for preventing financial calculation errors
 * and malicious input that could cause system instability
 * 
 * Usage Examples:
 * 
 * @ValidFinancialAmount
 * private BigDecimal amount;
 * 
 * @ValidFinancialAmount(currency = "USD", maxAmount = "10000.00")
 * private BigDecimal paymentAmount;
 * 
 * @ValidFinancialAmount(allowZero = false, allowNegative = false)
 * private BigDecimal transferAmount;
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FinancialAmountValidator.class)
@Documented
public @interface ValidFinancialAmount {
    
    /**
     * Default error message
     */
    String message() default "Invalid financial amount";
    
    /**
     * Validation groups
     */
    Class<?>[] groups() default {};
    
    /**
     * Payload for additional metadata
     */
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether zero values are allowed
     * Default: false (zero not allowed for most financial operations)
     */
    boolean allowZero() default false;
    
    /**
     * Whether negative values are allowed
     * Default: false (most financial amounts should be positive)
     */
    boolean allowNegative() default false;
    
    /**
     * Currency code for currency-specific validation
     * Empty string means no currency-specific rules
     */
    String currency() default "";
    
    /**
     * Maximum allowed amount as string
     * Empty string uses default maximum (999999999.9999)
     */
    String maxAmount() default "";
    
    /**
     * Minimum allowed amount as string
     * Empty string uses default minimum (0.0001)
     */
    String minAmount() default "";
    
    /**
     * Maximum number of decimal places allowed
     * 0 uses default (4 decimal places)
     */
    int maxDecimalPlaces() default 4;
    
    /**
     * Transaction type for additional validation
     * Used for type-specific business rules
     */
    TransactionType transactionType() default TransactionType.GENERAL;
    
    /**
     * Whether this is a high-risk transaction requiring additional validation
     */
    boolean highRisk() default false;
    
    /**
     * Transaction types for enhanced validation
     */
    enum TransactionType {
        GENERAL,        // Default validation
        PAYMENT,        // Payment transactions
        TRANSFER,       // Money transfers
        WITHDRAWAL,     // Withdrawals
        DEPOSIT,        // Deposits
        REFUND,         // Refunds
        FEE,           // Fee calculations
        CRYPTO,        // Cryptocurrency transactions
        INTERNATIONAL  // International transfers
    }
    
    /**
     * Define multiple @ValidFinancialAmount annotations
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        ValidFinancialAmount[] value();
    }
}