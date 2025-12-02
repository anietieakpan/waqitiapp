package com.waqiti.common.saga;

/**
 * Exception thrown during saga execution
 */
public class SagaExecutionException extends RuntimeException {
    
    private String sagaId;
    private String stepId;
    
    public SagaExecutionException(String message) {
        super(message);
    }
    
    public SagaExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SagaExecutionException(String sagaId, String message) {
        super(message);
        this.sagaId = sagaId;
    }
    
    public SagaExecutionException(String sagaId, String stepId, String message) {
        super(message);
        this.sagaId = sagaId;
        this.stepId = stepId;
    }
    
    public SagaExecutionException(String sagaId, String stepId, String message, Throwable cause) {
        super(message, cause);
        this.sagaId = sagaId;
        this.stepId = stepId;
    }
    
    public String getSagaId() {
        return sagaId;
    }
    
    public String getStepId() {
        return stepId;
    }
}