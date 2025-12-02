package com.waqiti.validation.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom validation exception for financial operations
 * 
 * Provides detailed error information for validation failures
 * including field-level errors and security context
 */
@Getter
public class ValidationException extends RuntimeException {
    
    private final List<FieldError> fieldErrors = new ArrayList<>();
    private final Map<String, Object> context;
    private final String errorCode;
    private final String operation;
    
    public ValidationException(String message) {
        super(message);
        this.errorCode = "VALIDATION_ERROR";
        this.operation = null;
        this.context = null;
    }
    
    public ValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.operation = null;
        this.context = null;
    }
    
    public ValidationException(String message, String errorCode, String operation) {
        super(message);
        this.errorCode = errorCode;
        this.operation = operation;
        this.context = null;
    }
    
    public ValidationException(String message, String errorCode, String operation, 
                             Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.operation = operation;
        this.context = context;
    }
    
    public ValidationException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.errorCode = "FIELD_VALIDATION_ERROR";
        this.operation = null;
        this.context = null;
        this.fieldErrors.addAll(fieldErrors);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "VALIDATION_ERROR";
        this.operation = null;
        this.context = null;
    }
    
    public void addFieldError(String field, String message) {
        this.fieldErrors.add(new FieldError(field, message));
    }
    
    public void addFieldError(String field, String message, Object rejectedValue) {
        this.fieldErrors.add(new FieldError(field, message, rejectedValue));
    }
    
    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
    
    /**
     * Field-level error information
     */
    @Getter
    public static class FieldError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
        
        public FieldError(String field, String message) {
            this(field, message, null);
        }
        
        public FieldError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
        
        @Override
        public String toString() {
            return String.format("Field '%s': %s", field, message);
        }
    }
    
    // Common validation error codes
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String INVALID_CURRENCY = "INVALID_CURRENCY";
    public static final String AMOUNT_TOO_SMALL = "AMOUNT_TOO_SMALL";
    public static final String AMOUNT_TOO_LARGE = "AMOUNT_TOO_LARGE";
    public static final String INVALID_SCALE = "INVALID_SCALE";
    public static final String CURRENCY_NOT_SUPPORTED = "CURRENCY_NOT_SUPPORTED";
    public static final String FRAUD_LIMIT_EXCEEDED = "FRAUD_LIMIT_EXCEEDED";
    public static final String USER_TIER_LIMIT_EXCEEDED = "USER_TIER_LIMIT_EXCEEDED";
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String CURRENCY_RESTRICTED = "CURRENCY_RESTRICTED";
    public static final String TRANSACTION_TYPE_INVALID = "TRANSACTION_TYPE_INVALID";
}