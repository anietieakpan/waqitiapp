package com.waqiti.common.saga;

/**
 * Exception thrown when a saga times out
 */
public class SagaTimeoutException extends SagaExecutionException {
    
    private long timeoutMs;
    
    public SagaTimeoutException(String message) {
        super(message);
    }
    
    public SagaTimeoutException(String message, long timeoutMs) {
        super(message);
        this.timeoutMs = timeoutMs;
    }
    
    public SagaTimeoutException(String sagaId, String message, long timeoutMs) {
        super(sagaId, message);
        this.timeoutMs = timeoutMs;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
}