package com.waqiti.saga.exception;

/**
 * Exception thrown when saga compensation fails
 * 
 * This is a critical exception that indicates a distributed transaction
 * could not be properly rolled back, requiring manual intervention.
 */
public class CompensationFailedException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final String sagaId;
    private final String failedStep;
    private final String serviceName;
    
    public CompensationFailedException(String message) {
        super(message);
        this.sagaId = null;
        this.failedStep = null;
        this.serviceName = null;
    }
    
    public CompensationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.sagaId = null;
        this.failedStep = null;
        this.serviceName = null;
    }
    
    public CompensationFailedException(String sagaId, String failedStep, String serviceName, String message) {
        super(message);
        this.sagaId = sagaId;
        this.failedStep = failedStep;
        this.serviceName = serviceName;
    }
    
    public CompensationFailedException(String sagaId, String failedStep, String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.sagaId = sagaId;
        this.failedStep = failedStep;
        this.serviceName = serviceName;
    }
    
    public String getSagaId() {
        return sagaId;
    }
    
    public String getFailedStep() {
        return failedStep;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    @Override
    public String toString() {
        return String.format("CompensationFailedException{sagaId='%s', failedStep='%s', serviceName='%s', message='%s'}", 
                sagaId, failedStep, serviceName, getMessage());
    }
}