package com.waqiti.corebanking.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for UUID format
 *
 * Validates that a string is a valid UUID (Universally Unique Identifier).
 * Supports both standard UUID format and custom prefixed formats (e.g., "acc-uuid", "txn-uuid").
 *
 * Usage:
 * <pre>
 * {@code
 * @ValidUUID
 * private String accountId;
 *
 * @ValidUUID(allowPrefix = true)
 * private String transactionId; // Accepts "txn-550e8400-e29b-41d4-a716-446655440000"
 * }
 * </pre>
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUUIDValidator.class)
@Documented
public @interface ValidUUID {

    String message() default "Invalid UUID format. Expected format: 8-4-4-4-12 hex digits";

    /**
     * Allow custom prefixes like "acc-", "txn-", "user-" before the UUID
     */
    boolean allowPrefix() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
