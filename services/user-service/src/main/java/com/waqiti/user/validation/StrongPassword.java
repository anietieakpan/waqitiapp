package com.waqiti.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * StrongPassword Validator
 *
 * Validates that a password meets strong password requirements:
 * - Minimum 8 characters (configurable)
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one digit
 * - At least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)
 * - No common weak passwords
 *
 * COMPLIANCE CONTEXT:
 * - NIST SP 800-63B: Password strength requirements
 * - PCI-DSS 8.2.3: Strong passwords required for financial systems
 * - OWASP: Password security best practices
 *
 * USAGE:
 * <pre>
 * {@code
 * public class PasswordChangeRequest {
 *     @StrongPassword
 *     private String newPassword;
 * }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
@Documented
public @interface StrongPassword {

    String message() default "Password does not meet strength requirements";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Minimum password length
     * Default: 8 characters (NIST minimum)
     */
    int minLength() default 8;

    /**
     * Require uppercase letter
     * Default: true
     */
    boolean requireUppercase() default true;

    /**
     * Require lowercase letter
     * Default: true
     */
    boolean requireLowercase() default true;

    /**
     * Require digit
     * Default: true
     */
    boolean requireDigit() default true;

    /**
     * Require special character
     * Default: true
     */
    boolean requireSpecialChar() default true;

    /**
     * Check against common weak passwords
     * Default: true
     */
    boolean checkCommonPasswords() default true;
}
