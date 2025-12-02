package com.waqiti.rewards.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator for referral codes
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Slf4j
public class ReferralCodeValidator implements ConstraintValidator<ValidReferralCode, String> {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[A-Z0-9]+$");
    private static final Pattern ALPHANUMERIC_WITH_SPECIAL = Pattern.compile("^[A-Z0-9-_]+$");

    private int minLength;
    private int maxLength;
    private boolean allowSpecialChars;

    @Override
    public void initialize(ValidReferralCode constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.maxLength = constraintAnnotation.maxLength();
        this.allowSpecialChars = constraintAnnotation.allowSpecialChars();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        // Check length
        if (value.length() < minLength || value.length() > maxLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    String.format("Referral code must be between %d and %d characters",
                            minLength, maxLength)
            ).addConstraintViolation();
            return false;
        }

        // Check pattern
        Pattern pattern = allowSpecialChars ? ALPHANUMERIC_WITH_SPECIAL : ALPHANUMERIC_PATTERN;
        if (!pattern.matcher(value).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    allowSpecialChars
                            ? "Referral code can only contain uppercase letters, numbers, hyphens, and underscores"
                            : "Referral code can only contain uppercase letters and numbers"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
