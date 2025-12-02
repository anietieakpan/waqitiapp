package com.waqiti.payment.exception;

/**
 * Exception thrown when compliance validation fails
 */
public class ComplianceException extends RuntimeException {
    
    public ComplianceException(String message) {
        super(message);
    }
    
    public ComplianceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ComplianceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}