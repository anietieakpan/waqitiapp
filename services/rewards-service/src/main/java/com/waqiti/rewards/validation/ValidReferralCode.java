package com.waqiti.rewards.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validation annotation for referral codes
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Documented
@Constraint(validatedBy = ReferralCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidReferralCode {

    String message() default "Invalid referral code format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Minimum length for referral code
     */
    int minLength() default 6;

    /**
     * Maximum length for referral code
     */
    int maxLength() default 12;

    /**
     * Whether to allow special characters
     */
    boolean allowSpecialChars() default false;
}
