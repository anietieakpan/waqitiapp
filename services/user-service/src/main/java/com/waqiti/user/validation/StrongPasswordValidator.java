package com.waqiti.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * StrongPassword Validator Implementation
 *
 * Validates passwords against security best practices
 */
@Slf4j
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // Password complexity patterns
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN =
        Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");

    // Common weak passwords (top 100 most common)
    // In production, this should be a much larger list or external service
    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
        "password", "123456", "12345678", "1234", "qwerty", "12345", "dragon",
        "baseball", "football", "letmein", "monkey", "abc123", "mustang",
        "access", "shadow", "master", "michael", "superman", "696969",
        "123123", "batman", "trustno1", "password1", "qwerty123",
        "welcome", "admin", "changeme", "test", "demo", "user",
        "Password1", "Password123", "Passw0rd", "P@ssw0rd", "Welcome1"
    );

    private int minLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecialChar;
    private boolean checkCommonPasswords;

    @Override
    public void initialize(StrongPassword constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.requireUppercase = constraintAnnotation.requireUppercase();
        this.requireLowercase = constraintAnnotation.requireLowercase();
        this.requireDigit = constraintAnnotation.requireDigit();
        this.requireSpecialChar = constraintAnnotation.requireSpecialChar();
        this.checkCommonPasswords = constraintAnnotation.checkCommonPasswords();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Null values handled by @NotNull
        if (password == null) {
            return true;
        }

        // Check minimum length
        if (password.length() < minLength) {
            setCustomMessage(context,
                String.format("Password must be at least %d characters long", minLength));
            return false;
        }

        // Check maximum length (prevent DoS attacks)
        if (password.length() > 128) {
            setCustomMessage(context, "Password must not exceed 128 characters");
            return false;
        }

        // Check uppercase requirement
        if (requireUppercase && !UPPERCASE_PATTERN.matcher(password).matches()) {
            setCustomMessage(context, "Password must contain at least one uppercase letter");
            return false;
        }

        // Check lowercase requirement
        if (requireLowercase && !LOWERCASE_PATTERN.matcher(password).matches()) {
            setCustomMessage(context, "Password must contain at least one lowercase letter");
            return false;
        }

        // Check digit requirement
        if (requireDigit && !DIGIT_PATTERN.matcher(password).matches()) {
            setCustomMessage(context, "Password must contain at least one digit");
            return false;
        }

        // Check special character requirement
        if (requireSpecialChar && !SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            setCustomMessage(context,
                "Password must contain at least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)");
            return false;
        }

        // Check against common weak passwords
        if (checkCommonPasswords && isCommonPassword(password)) {
            setCustomMessage(context,
                "Password is too common. Please choose a stronger password.");
            log.warn("SECURITY: Attempt to use common weak password");
            return false;
        }

        // Check for sequential characters (123, abc)
        if (hasSequentialChars(password)) {
            setCustomMessage(context,
                "Password contains sequential characters. Please choose a stronger password.");
            return false;
        }

        // Check for repeated characters (aaa, 111)
        if (hasTooManyRepeatedChars(password)) {
            setCustomMessage(context,
                "Password has too many repeated characters. Please choose a stronger password.");
            return false;
        }

        return true;
    }

    /**
     * Check if password is in common weak passwords list
     */
    private boolean isCommonPassword(String password) {
        // Case-insensitive check
        return COMMON_WEAK_PASSWORDS.contains(password.toLowerCase());
    }

    /**
     * Check for sequential characters (123456, abcdef)
     */
    private boolean hasSequentialChars(String password) {
        String lower = password.toLowerCase();

        for (int i = 0; i < lower.length() - 2; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            char c3 = lower.charAt(i + 2);

            // Check sequential numbers or letters
            if ((c2 == c1 + 1) && (c3 == c2 + 1)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for too many repeated characters (aaaa, 1111)
     */
    private boolean hasTooManyRepeatedChars(String password) {
        int maxRepeated = 1;
        int currentRepeated = 1;

        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                currentRepeated++;
                maxRepeated = Math.max(maxRepeated, currentRepeated);
            } else {
                currentRepeated = 1;
            }
        }

        // If more than 3 consecutive repeated characters
        return maxRepeated > 3;
    }

    /**
     * Set custom validation message
     */
    private void setCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
