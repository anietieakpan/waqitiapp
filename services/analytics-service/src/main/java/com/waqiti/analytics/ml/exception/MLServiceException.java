package com.waqiti.analytics.ml.exception;

/**
 * Exception thrown when ML service operations fail
 */
public class MLServiceException extends RuntimeException {
    
    public MLServiceException(String message) {
        super(message);
    }
    
    public MLServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public MLServiceException(Throwable cause) {
        super(cause);
    }
}