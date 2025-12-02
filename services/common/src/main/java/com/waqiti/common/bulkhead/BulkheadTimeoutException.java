package com.waqiti.common.bulkhead;

/**
 * Exception thrown when a bulkhead operation times out
 */
public class BulkheadTimeoutException extends BulkheadException {
    
    public BulkheadTimeoutException(String message) {
        super(message);
    }
    
    public BulkheadTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}