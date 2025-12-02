package com.waqiti.common.compliance.exception;

/**
 * Exception thrown when compliance report validation fails
 */
public class ComplianceValidationException extends RuntimeException {

    private final String fieldName;
    private final Object invalidValue;
    private final String validationRule;

    public ComplianceValidationException(String message) {
        super(message);
        this.fieldName = null;
        this.invalidValue = null;
        this.validationRule = null;
    }

    public ComplianceValidationException(String message, Throwable cause) {
        super(message, cause);
        this.fieldName = null;
        this.invalidValue = null;
        this.validationRule = null;
    }

    public ComplianceValidationException(String fieldName, Object invalidValue, String validationRule) {
        super(String.format("Validation failed for field '%s' with value '%s': %s",
            fieldName, invalidValue, validationRule));
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
        this.validationRule = validationRule;
    }

    public ComplianceValidationException(String fieldName, String message) {
        super(String.format("Validation failed for field '%s': %s", fieldName, message));
        this.fieldName = fieldName;
        this.invalidValue = null;
        this.validationRule = message;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }

    public String getValidationRule() {
        return validationRule;
    }

    /**
     * Create exception for required field
     */
    public static ComplianceValidationException requiredField(String fieldName) {
        return new ComplianceValidationException(fieldName, "Field is required");
    }

    /**
     * Create exception for invalid format
     */
    public static ComplianceValidationException invalidFormat(String fieldName, Object value, String expectedFormat) {
        return new ComplianceValidationException(fieldName, value,
            "Invalid format. Expected: " + expectedFormat);
    }

    /**
     * Create exception for invalid value
     */
    public static ComplianceValidationException invalidValue(String fieldName, Object value, String reason) {
        return new ComplianceValidationException(fieldName, value, reason);
    }

    /**
     * Create exception for range violation
     */
    public static ComplianceValidationException outOfRange(String fieldName, Object value, Object min, Object max) {
        return new ComplianceValidationException(fieldName, value,
            String.format("Value must be between %s and %s", min, max));
    }
}
