package com.waqiti.common.bulkhead;

import lombok.Builder;
import lombok.Data;

/**
 * Result of bulkhead-protected operation execution
 */
@Data
@Builder
public class BulkheadResult<T> {
    
    private boolean success;
    private T result;
    private ResourceType resourceType;
    private String operationId;
    private String error;
    private long executionTime;
    private boolean timeout;
    
    public boolean isFailure() {
        return !success;
    }
    
    public boolean isTimeout() {
        return timeout;
    }
    
    public boolean hasResult() {
        return result != null;
    }
    
    public boolean isRejected() {
        return !success && error != null && error.contains("rejected");
    }
    
    public Exception getException() {
        if (error != null) {
            return new RuntimeException(error);
        }
        return null;
    }
}