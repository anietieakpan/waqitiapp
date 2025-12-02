package com.waqiti.payment.exception;

/**
 * Exception thrown when a transaction limit is exceeded
 */
public class LimitExceededException extends RuntimeException {
    
    public LimitExceededException(String message) {
        super(message);
    }
    
    public LimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}