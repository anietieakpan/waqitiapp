package com.waqiti.common.validation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Comprehensive validation utility class for robust input validation
 * Provides null-safe operations and common validation patterns
 */
@UtilityClass
@Slf4j
public class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );
    
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
        "^[A-Z0-9]{10,20}$"
    );

    /**
     * Validates that an object is not null
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * Validates that a string is not null or empty
     */
    public static String requireNonBlank(String str, String message) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(message);
        }
        return str.trim();
    }

    /**
     * Validates that a collection is not null or empty
     */
    public static <T extends Collection<?>> T requireNonEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * Validates a BigDecimal amount is positive
     */
    public static BigDecimal requirePositive(BigDecimal amount, String message) {
        requireNonNull(amount, message);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return amount;
    }

    /**
     * Validates a BigDecimal amount is non-negative
     */
    public static BigDecimal requireNonNegative(BigDecimal amount, String message) {
        requireNonNull(amount, message);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(message);
        }
        return amount;
    }

    /**
     * Validates an email address format
     */
    public static String validateEmail(String email) {
        requireNonBlank(email, "Email cannot be blank");
        if (!EMAIL_PATTERN.matcher(email.toLowerCase()).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
        return email.toLowerCase();
    }

    /**
     * Validates a phone number format
     */
    public static String validatePhoneNumber(String phone) {
        requireNonBlank(phone, "Phone number cannot be blank");
        String cleaned = phone.replaceAll("[\\s()-]", "");
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("Invalid phone number format: " + phone);
        }
        return cleaned;
    }

    /**
     * Validates an account number format
     */
    public static String validateAccountNumber(String accountNumber) {
        requireNonBlank(accountNumber, "Account number cannot be blank");
        String cleaned = accountNumber.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (!ACCOUNT_NUMBER_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("Invalid account number format: " + accountNumber);
        }
        return cleaned;
    }

    /**
     * Validates a UUID string
     */
    public static UUID validateUUID(String uuidStr) {
        requireNonBlank(uuidStr, "UUID string cannot be blank");
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + uuidStr, e);
        }
    }

    /**
     * Validates that a value is within a range
     */
    public static <T extends Comparable<T>> T requireInRange(T value, T min, T max, String message) {
        requireNonNull(value, message);
        requireNonNull(min, "Min value cannot be null");
        requireNonNull(max, "Max value cannot be null");
        
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(message + " Value: " + value + 
                ", Range: [" + min + ", " + max + "]");
        }
        return value;
    }

    /**
     * Safe string length validation
     */
    public static String validateLength(String str, int minLength, int maxLength, String fieldName) {
        requireNonBlank(str, fieldName + " cannot be blank");
        if (str.length() < minLength || str.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be between " + 
                minLength + " and " + maxLength + " characters");
        }
        return str;
    }

    /**
     * Safe optional unwrapping with validation
     */
    public static <T> T requirePresent(Optional<T> optional, String message) {
        return optional.orElseThrow(() -> new IllegalArgumentException(message));
    }

    /**
     * Safe optional unwrapping with default value
     */
    public static <T> T getOrDefault(Optional<T> optional, T defaultValue) {
        return optional.orElse(defaultValue);
    }

    /**
     * Safe optional unwrapping with supplier
     */
    public static <T> T getOrElseGet(Optional<T> optional, Supplier<T> supplier) {
        return optional.orElseGet(supplier);
    }

    /**
     * Validates multiple conditions
     */
    public static void validateAll(ValidationCondition... conditions) {
        for (ValidationCondition condition : conditions) {
            if (!condition.test()) {
                throw new IllegalArgumentException(condition.getMessage());
            }
        }
    }

    /**
     * Validation condition interface
     */
    public interface ValidationCondition {
        boolean test();
        String getMessage();
    }

    /**
     * Creates a validation condition
     */
    public static ValidationCondition condition(Supplier<Boolean> test, String message) {
        return new ValidationCondition() {
            @Override
            public boolean test() {
                try {
                    return test.get();
                } catch (Exception e) {
                    log.error("Validation condition failed", e);
                    return false;
                }
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }

    /**
     * Sanitizes user input to prevent injection attacks
     */
    public static String sanitizeInput(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        // Remove potential SQL injection patterns
        String sanitized = input.replaceAll("['\"\\\\;]", "");
        // Remove potential XSS patterns
        sanitized = sanitized.replaceAll("<[^>]*>", "");
        // Remove control characters
        sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        return sanitized.trim();
    }

    /**
     * Validates and sanitizes a SQL identifier
     */
    public static String validateSQLIdentifier(String identifier) {
        requireNonBlank(identifier, "SQL identifier cannot be blank");
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    /**
     * Safe equals check with null handling
     */
    public static boolean safeEquals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * Safe string comparison with null handling
     */
    public static boolean safeStringEquals(String a, String b, boolean ignoreCase) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
    }
}