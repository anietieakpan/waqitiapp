package com.waqiti.validation.annotation;

import com.waqiti.validation.CurrencyValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Currency Code Validation Annotation
 * 
 * Validates currency codes with comprehensive business rules:
 * - Valid ISO 4217 currency codes
 * - Supported currencies only
 * - Regional compliance checks
 * - Cryptocurrency support
 * - Cross-field validation compatibility
 * 
 * SECURITY: Prevents invalid currency processing that could lead to
 * incorrect exchange rates, regulatory violations, or fraud
 * 
 * Usage Examples:
 * 
 * @ValidCurrency
 * private String currency;
 * 
 * @ValidCurrency(supportedOnly = true)
 * private String transactionCurrency;
 * 
 * @ValidCurrency(allowCrypto = true, region = "US")
 * private String paymentCurrency;
 * 
 * @ValidCurrency(restrictedCurrencies = {"IRR", "KPW"})
 * private String internationalTransferCurrency;
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CurrencyValidator.class)
@Documented
public @interface ValidCurrency {
    
    /**
     * Default error message
     */
    String message() default "Invalid currency code";
    
    /**
     * Validation groups
     */
    Class<?>[] groups() default {};
    
    /**
     * Payload for additional metadata
     */
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether to only allow supported currencies
     * If false, allows any valid ISO 4217 code
     */
    boolean supportedOnly() default true;
    
    /**
     * Whether to allow cryptocurrency codes
     */
    boolean allowCrypto() default false;
    
    /**
     * Whether to allow stablecoins
     */
    boolean allowStablecoins() default false;
    
    /**
     * Geographic region for compliance validation
     * Empty string means no regional restrictions
     */
    String region() default "";
    
    /**
     * Transaction type for currency compatibility
     */
    TransactionType transactionType() default TransactionType.GENERAL;
    
    /**
     * Specific currencies that are not allowed
     * Used for sanctions compliance
     */
    String[] restrictedCurrencies() default {};
    
    /**
     * Required currencies (if specified, only these are allowed)
     */
    String[] requiredCurrencies() default {};
    
    /**
     * Whether this currency field must match another currency field
     * Used for source/destination currency validation
     */
    String matchField() default "";
    
    /**
     * Whether to validate currency against user's allowed currencies
     */
    boolean checkUserPermissions() default false;
    
    /**
     * Whether to validate currency is currently active/trading
     */
    boolean checkActiveStatus() default true;
    
    /**
     * Transaction types for currency validation
     */
    enum TransactionType {
        GENERAL,           // General purpose
        PAYMENT,           // Payment processing
        TRANSFER,          // Money transfers
        EXCHANGE,          // Currency exchange
        INTERNATIONAL,     // International transfers
        CRYPTO,            // Cryptocurrency transactions
        INVESTMENT,        // Investment transactions
        LENDING,           // Lending operations
        REMITTANCE        // Remittance services
    }
    
    /**
     * Geographic regions for compliance
     */
    enum Region {
        US,               // United States
        EU,               // European Union
        UK,               // United Kingdom
        ASIA_PACIFIC,     // Asia-Pacific
        MIDDLE_EAST,      // Middle East
        AFRICA,           // Africa
        LATIN_AMERICA,    // Latin America
        GLOBAL            // No regional restrictions
    }
    
    /**
     * Define multiple @ValidCurrency annotations
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        ValidCurrency[] value();
    }
}