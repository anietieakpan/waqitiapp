package com.waqiti.corebanking.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for transaction amounts
 *
 * Validates that transaction amounts are within acceptable ranges:
 * - Minimum: 0.01 (prevents zero-value transactions)
 * - Maximum: 1,000,000.00 (AML compliance, prevents suspicious transactions)
 * - Scale: Maximum 4 decimal places (financial precision)
 *
 * For transactions exceeding the maximum, manual approval workflow is required.
 *
 * Usage:
 * <pre>
 * {@code
 * @TransactionAmount
 * private BigDecimal amount;
 * }
 * </pre>
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TransactionAmountValidator.class)
@Documented
public @interface TransactionAmount {

    String message() default "Transaction amount must be between 0.01 and 1,000,000.00 with max 4 decimal places";

    /**
     * Minimum allowed amount (default: 0.01)
     */
    String min() default "0.01";

    /**
     * Maximum allowed amount (default: 1,000,000.00)
     * Transactions above this require manual approval
     */
    String max() default "1000000.00";

    /**
     * Maximum decimal places (default: 4)
     */
    int scale() default 4;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
