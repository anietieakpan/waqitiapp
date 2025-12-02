package com.waqiti.common.saga;

/**
 * Exception thrown when there's an issue with saga state management
 */
public class SagaStateException extends SagaExecutionException {
    
    public SagaStateException(String message) {
        super(message);
    }
    
    public SagaStateException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SagaStateException(String sagaId, String message) {
        super(sagaId, message);
    }
    
    public SagaStateException(String sagaId, String message, Throwable cause) {
        super(sagaId, null, message, cause);
    }
}