package com.waqiti.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation annotation for bank account numbers.
 * Validates format and checksum for various account number types.
 */
@Documented
@Constraint(validatedBy = AccountNumberValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAccountNumber {
    
    String message() default "Invalid account number";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Type of account number to validate
     */
    AccountNumberType type() default AccountNumberType.GENERIC;
    
    /**
     * Whether to perform checksum validation
     */
    boolean validateChecksum() default true;
    
    /**
     * Country code for country-specific validation (ISO 3166-1 alpha-2)
     */
    String countryCode() default "";
    
    enum AccountNumberType {
        GENERIC,      // Generic account number
        IBAN,         // International Bank Account Number
        ROUTING_ABA,  // US ABA routing number
        ACCOUNT_US,   // US account number
        SORT_CODE_UK, // UK sort code
        SWIFT_BIC,    // SWIFT/BIC code
        INTERNAL      // Internal wallet/account ID
    }
}