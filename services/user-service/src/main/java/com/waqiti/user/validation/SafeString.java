package com.waqiti.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * SafeString Validator
 *
 * Validates that a string does not contain malicious content:
 * - XSS (Cross-Site Scripting) attacks
 * - SQL Injection attempts
 * - Script tags and JavaScript
 * - HTML event handlers
 * - Common injection patterns
 *
 * SECURITY CONTEXT:
 * Financial systems are prime targets for injection attacks.
 * This validator provides defense-in-depth against common attack vectors.
 *
 * OWASP REFERENCES:
 * - A03:2021 – Injection
 * - XSS Prevention Cheat Sheet
 * - SQL Injection Prevention Cheat Sheet
 *
 * USAGE:
 * <pre>
 * {@code
 * public class UserDTO {
 *     @SafeString
 *     private String username;
 *
 *     @SafeString(allowHtml = true) // For rich text fields
 *     private String bio;
 * }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeStringValidator.class)
@Documented
public @interface SafeString {

    String message() default "Invalid or potentially malicious characters detected";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Whether to allow limited HTML tags (sanitized)
     * Default: false (no HTML allowed)
     */
    boolean allowHtml() default false;

    /**
     * Whether to allow special characters (!@#$%^&*)
     * Default: true
     */
    boolean allowSpecialChars() default true;

    /**
     * Maximum length (0 = no limit)
     * Default: 0 (no limit - use @Size for length constraints)
     */
    int maxLength() default 0;
}
