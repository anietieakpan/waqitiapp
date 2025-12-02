package com.waqiti.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation annotation for monetary amounts.
 * Ensures amounts are positive and within acceptable limits.
 */
@Documented
@Constraint(validatedBy = AmountValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAmount {
    
    String message() default "Invalid amount";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Minimum amount (inclusive). Default is 0.01
     */
    String min() default "0.01";
    
    /**
     * Maximum amount (inclusive). Default is 1000000.00
     */
    String max() default "1000000.00";
    
    /**
     * Whether to allow zero amounts
     */
    boolean allowZero() default false;
    
    /**
     * Maximum number of decimal places allowed
     */
    int maxDecimalPlaces() default 2;
    
    /**
     * Currency code for validation (optional)
     */
    String currency() default "";
}