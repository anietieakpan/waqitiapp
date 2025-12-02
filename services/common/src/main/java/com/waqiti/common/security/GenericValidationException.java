package com.waqiti.common.security;

/**
 * Generic Validation Exception
 *
 * Used for input validation failures across all services
 */
public class GenericValidationException extends RuntimeException {

    private String field;
    private Object rejectedValue;
    private String validationCode;

    public GenericValidationException(String message) {
        super(message);
    }

    public GenericValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public GenericValidationException(String field, Object rejectedValue, String message) {
        super(message);
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    public GenericValidationException(String field, String validationCode, String message) {
        super(message);
        this.field = field;
        this.validationCode = validationCode;
    }

    public String getField() {
        return field;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public String getValidationCode() {
        return validationCode;
    }
}
