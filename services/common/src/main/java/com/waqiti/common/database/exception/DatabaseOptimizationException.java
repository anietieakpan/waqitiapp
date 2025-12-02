package com.waqiti.common.database.exception;

/**
 * Exception thrown when database optimization operations fail
 */
public class DatabaseOptimizationException extends RuntimeException {
    
    public DatabaseOptimizationException(String message) {
        super(message);
    }
    
    public DatabaseOptimizationException(String message, Throwable cause) {
        super(message, cause);
    }
}