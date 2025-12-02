package com.waqiti.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * ValidPhoneNumber Validator
 *
 * Validates phone numbers in international E.164 format
 *
 * FORMAT: +[country code][number]
 * Example: +14155552671 (US)
 *          +442071838750 (UK)
 *          +819012345678 (Japan)
 *
 * VALIDATION:
 * - Must start with '+'
 * - Must contain only digits after '+'
 * - Length: 10-15 characters (including '+')
 * - Country code: 1-3 digits
 *
 * USAGE:
 * <pre>
 * {@code
 * public class UserDTO {
 *     @ValidPhoneNumber
 *     private String phoneNumber;
 * }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPhoneNumberValidator.class)
@Documented
public @interface ValidPhoneNumber {

    String message() default "Invalid phone number format. Use E.164 format: +[country code][number]";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Whether to allow null values
     * Default: true (use @NotNull separately for null checks)
     */
    boolean allowNull() default true;
}
