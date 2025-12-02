package com.waqiti.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation annotation for phone numbers.
 * Validates international phone number formats.
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhoneNumber {
    
    String message() default "Invalid phone number";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether to require international format (E.164)
     */
    boolean requireInternationalFormat() default true;
    
    /**
     * Specific country code to validate against (ISO 3166-1 alpha-2)
     */
    String countryCode() default "";
    
    /**
     * Whether to allow mobile numbers only
     */
    boolean mobileOnly() default false;
}