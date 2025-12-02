package com.waqiti.analytics.exception;

/**
 * Exception for query optimization errors
 */
public class QueryOptimizationException extends RuntimeException {
    
    public QueryOptimizationException(String message) {
        super(message);
    }
    
    public QueryOptimizationException(String message, Throwable cause) {
        super(message, cause);
    }
}