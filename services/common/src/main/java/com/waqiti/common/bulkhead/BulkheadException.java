package com.waqiti.common.bulkhead;

/**
 * Base exception for bulkhead operations
 */
public class BulkheadException extends RuntimeException {
    
    public BulkheadException(String message) {
        super(message);
    }
    
    public BulkheadException(String message, Throwable cause) {
        super(message, cause);
    }
}