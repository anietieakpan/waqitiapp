package com.waqiti.common.bulkhead;

/**
 * Exception thrown when a bulkhead operation is rejected due to resource exhaustion
 */
public class BulkheadRejectedException extends BulkheadException {
    
    public BulkheadRejectedException(String message) {
        super(message);
    }
    
    public BulkheadRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}