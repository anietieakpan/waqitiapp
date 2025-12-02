package com.waqiti.corebanking.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for ISO 4217 currency codes
 *
 * Validates that the currency code is a valid 3-letter ISO 4217 code.
 * Commonly used for financial transactions to ensure only valid currencies.
 *
 * Usage:
 * <pre>
 * {@code
 * @CurrencyCode
 * private String currency;
 * }
 * </pre>
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Documented
public @interface CurrencyCode {

    String message() default "Invalid currency code. Must be a valid ISO 4217 3-letter code (e.g., USD, EUR, GBP)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
