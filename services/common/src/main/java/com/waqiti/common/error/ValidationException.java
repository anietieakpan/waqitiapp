package com.waqiti.common.error;

import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when validation fails with multiple errors.
 *
 * Features:
 * - Multiple validation error support
 * - Field-level error tracking
 * - Error code integration
 * - Detailed error messages
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Getter
public class ValidationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;
    private final String field;
    private final Map<String, List<String>> fieldErrors;

    @Getter(AccessLevel.NONE) // Prevent Lombok from generating getErrorCode() - parent has it
    private final ErrorCode errorCodeEnum;

    /**
     * Constructor with message and errors
     */
    public ValidationException(String message, List<String> errors) {
        super(ErrorCode.VALIDATION_FAILED.getCode(), message, HttpStatus.BAD_REQUEST.value());
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.field = null;
        this.fieldErrors = new HashMap<>();
        this.errorCodeEnum = ErrorCode.VALIDATION_FAILED;
    }

    /**
     * Constructor with message, field, and errors
     */
    public ValidationException(String message, String field, List<String> errors) {
        super(ErrorCode.VALIDATION_FAILED.getCode(), message, HttpStatus.BAD_REQUEST.value());
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.field = field;
        this.fieldErrors = new HashMap<>();
        if (field != null && errors != null) {
            this.fieldErrors.put(field, new ArrayList<>(errors));
        }
        this.errorCodeEnum = ErrorCode.VALIDATION_FAILED;
    }

    /**
     * Constructor with single error message
     */
    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED.getCode(), message, HttpStatus.BAD_REQUEST.value());
        this.errors = List.of(message);
        this.field = null;
        this.fieldErrors = new HashMap<>();
        this.errorCodeEnum = ErrorCode.VALIDATION_FAILED;
    }

    /**
     * Constructor with field and single error
     */
    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED.getCode(),
            String.format("Validation failed for field '%s': %s", field, message),
            HttpStatus.BAD_REQUEST.value());
        this.field = field;
        this.errors = List.of(message);
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(field, List.of(message));
        this.errorCodeEnum = ErrorCode.VALIDATION_FAILED;
    }

    /**
     * Constructor with field errors map
     */
    public ValidationException(String message, Map<String, List<String>> fieldErrors) {
        super(ErrorCode.VALIDATION_FAILED.getCode(), message, HttpStatus.BAD_REQUEST.value());
        this.errors = new ArrayList<>();
        this.field = null;
        this.fieldErrors = fieldErrors != null ? new HashMap<>(fieldErrors) : new HashMap<>();
        this.errorCodeEnum = ErrorCode.VALIDATION_FAILED;

        // Flatten field errors into errors list
        if (fieldErrors != null) {
            fieldErrors.values().forEach(errors::addAll);
        }
    }

    /**
     * Constructor with custom error code
     */
    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode.getCode(), message, HttpStatus.BAD_REQUEST.value());
        this.errors = List.of(message);
        this.field = null;
        this.fieldErrors = new HashMap<>();
        this.errorCodeEnum = errorCode;
    }

    /**
     * Constructor with cause
     */
    public ValidationException(String message, Throwable cause) {
        super(ErrorCode.VALIDATION_FAILED.getCode(), message, HttpStatus.BAD_REQUEST.value(), cause);
        this.errors = List.of(message);
        this.field = null;
        this.fieldErrors = new HashMap<>();
        this.errorCodeEnum = ErrorCode.VALIDATION_FAILED;
    }

    /**
     * Get formatted error message
     */
    public String getFormattedMessage() {
        if (errors.isEmpty()) {
            return getMessage();
        }

        StringBuilder sb = new StringBuilder(getMessage());
        sb.append(": ");

        if (errors.size() == 1) {
            sb.append(errors.get(0));
        } else {
            sb.append("\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(errors.get(i));
                if (i < errors.size() - 1) {
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Check if there are multiple errors
     */
    public boolean hasMultipleErrors() {
        return errors.size() > 1;
    }

    /**
     * Get error count
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Add a field error
     */
    public void addFieldError(String field, String error) {
        this.fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(error);
        this.errors.add(error);
    }

    /**
     * Check if a specific field has errors
     */
    public boolean hasFieldError(String field) {
        return fieldErrors.containsKey(field) && !fieldErrors.get(field).isEmpty();
    }

    /**
     * Get errors for a specific field
     */
    public List<String> getFieldErrors(String field) {
        return fieldErrors.getOrDefault(field, new ArrayList<>());
    }

    // ===== Static Factory Methods =====

    /**
     * Create validation exception for required field
     */
    public static ValidationException requiredField(String field) {
        return new ValidationException(
            field,
            String.format("Field '%s' is required", field)
        );
    }

    /**
     * Create validation exception for invalid format
     */
    public static ValidationException invalidFormat(String field, String expectedFormat) {
        return new ValidationException(
            field,
            String.format("Invalid format for field '%s'. Expected: %s", field, expectedFormat)
        );
    }

    /**
     * Create validation exception for out of range value
     */
    public static ValidationException outOfRange(String field, Object min, Object max) {
        return new ValidationException(
            field,
            String.format("Value for field '%s' must be between %s and %s", field, min, max)
        );
    }

    /**
     * Create validation exception for invalid length
     */
    public static ValidationException invalidLength(String field, int minLength, int maxLength) {
        return new ValidationException(
            field,
            String.format("Field '%s' length must be between %d and %d characters",
                field, minLength, maxLength)
        );
    }

    /**
     * Create validation exception for pattern mismatch
     */
    public static ValidationException patternMismatch(String field, String pattern) {
        return new ValidationException(
            field,
            String.format("Field '%s' does not match required pattern: %s", field, pattern)
        );
    }

    /**
     * Create validation exception for invalid email
     */
    public static ValidationException invalidEmail(String email) {
        return new ValidationException(
            ErrorCode.VAL_INVALID_EMAIL,
            String.format("Invalid email address: %s", email)
        );
    }

    /**
     * Create validation exception for invalid phone
     */
    public static ValidationException invalidPhone(String phone) {
        return new ValidationException(
            ErrorCode.VAL_INVALID_PHONE,
            String.format("Invalid phone number: %s", phone)
        );
    }

    /**
     * Create validation exception for invalid date
     */
    public static ValidationException invalidDate(String field, String reason) {
        return new ValidationException(
            ErrorCode.VAL_INVALID_DATE,
            String.format("Invalid date for field '%s': %s", field, reason)
        );
    }

    /**
     * Create validation exception for duplicate value
     */
    public static ValidationException duplicateValue(String field, Object value) {
        return new ValidationException(
            ErrorCode.VAL_DUPLICATE_VALUE,
            String.format("Value '%s' already exists for field '%s'", value, field)
        );
    }

    /**
     * Create validation exception with multiple field errors
     */
    public static ValidationException withFieldErrors(Map<String, List<String>> fieldErrors) {
        return new ValidationException("Validation failed for multiple fields", fieldErrors);
    }

    /**
     * Get the ErrorCode enum for this validation exception
     */
    public ErrorCode getErrorCodeEnum() {
        return errorCodeEnum;
    }

    @Override
    public String toString() {
        return String.format("ValidationException[errorCode=%s, field=%s, errorCount=%d, message=%s]",
            super.getErrorCode(), field, errors.size(), getMessage());
    }
}
