package com.waqiti.common.exception;

/**
 * Exception thrown when concurrent access issues occur
 */
public class ConcurrencyException extends BusinessException {
    
    private final String resourceId;
    private final String operation;
    
    public ConcurrencyException(String message) {
        super(message, "CONCURRENCY_ERROR");
        this.resourceId = null;
        this.operation = null;
    }
    
    public ConcurrencyException(String message, String resourceId, String operation) {
        super(message, "CONCURRENCY_ERROR");
        this.resourceId = resourceId;
        this.operation = operation;
    }
    
    public ConcurrencyException(String message, Throwable cause) {
        super(message, "CONCURRENCY_ERROR", cause);
        this.resourceId = null;
        this.operation = null;
    }
    
    public ConcurrencyException(String message, String resourceId, String operation, Throwable cause) {
        super(message, "CONCURRENCY_ERROR", cause);
        this.resourceId = resourceId;
        this.operation = operation;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public String getOperation() {
        return operation;
    }
}