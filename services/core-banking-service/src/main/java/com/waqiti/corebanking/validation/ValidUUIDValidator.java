package com.waqiti.corebanking.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validator implementation for @ValidUUID annotation
 *
 * Validates UUID format with support for:
 * - Standard UUID format: 550e8400-e29b-41d4-a716-446655440000
 * - Prefixed format: txn-550e8400-e29b-41d4-a716-446655440000
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public class ValidUUIDValidator implements ConstraintValidator<ValidUUID, String> {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Pattern PREFIXED_UUID_PATTERN = Pattern.compile(
        "^[a-z]+-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private boolean allowPrefix;

    @Override
    public void initialize(ValidUUID constraintAnnotation) {
        this.allowPrefix = constraintAnnotation.allowPrefix();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Use @NotBlank for null/blank checks
        }

        // Try to parse as standard UUID first
        if (UUID_PATTERN.matcher(value).matches()) {
            try {
                UUID.fromString(value);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        // If prefixes are allowed, check prefixed format
        if (allowPrefix && PREFIXED_UUID_PATTERN.matcher(value).matches()) {
            // Extract UUID part after the prefix
            String[] parts = value.split("-", 2);
            if (parts.length == 2) {
                try {
                    UUID.fromString(parts[1]);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
        }

        return false;
    }
}
