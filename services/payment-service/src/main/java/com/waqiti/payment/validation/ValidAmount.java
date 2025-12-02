package com.waqiti.payment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * PRODUCTION VALIDATOR: Payment Amount Validation
 *
 * Validates that a payment amount is:
 * 1. Not null
 * 2. Greater than zero
 * 3. Does not exceed maximum allowed amount
 * 4. Has proper decimal precision (max 2 decimal places for most currencies)
 * 5. Not negative
 * 6. Within reasonable bounds (not astronomical)
 *
 * SECURITY BENEFITS:
 * -----------------
 * - Prevents negative amount attacks (stealing money)
 * - Blocks unrealistic amounts (money laundering detection)
 * - Enforces precision rules (prevents rounding exploitation)
 * - Validates business rules at input boundary
 *
 * USAGE:
 * ------
 * @ValidAmount(max = "1000000.00", message = "Amount exceeds maximum allowed")
 * private BigDecimal amount;
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since November 17, 2025
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AmountValidator.class)
@Documented
public @interface ValidAmount {

    /**
     * Error message when validation fails
     */
    String message() default "Invalid payment amount";

    /**
     * Validation groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * Minimum allowed amount (default: 0.01 - one cent)
     */
    String min() default "0.01";

    /**
     * Maximum allowed amount (default: 1 million)
     */
    String max() default "1000000.00";

    /**
     * Maximum decimal places allowed (default: 2 for USD/EUR)
     */
    int maxDecimalPlaces() default 2;

    /**
     * Whether zero is allowed (for some operations like balance checks)
     */
    boolean allowZero() default false;
}
