package com.waqiti.analytics.exception;

/**
 * Exception for data access errors
 */
public class DataAccessException extends RuntimeException {
    
    public DataAccessException(String message) {
        super(message);
    }
    
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}