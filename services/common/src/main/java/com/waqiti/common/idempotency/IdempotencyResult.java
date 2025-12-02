package com.waqiti.common.idempotency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result of an idempotency check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyResult<T> {
    
    private T result;
    private IdempotencyStatus status;
    private UUID operationId;
    private String error;
    private boolean isNewExecution;
    
    // Legacy fields for backward compatibility
    private boolean isDuplicate;
    private boolean isInProgress;
    
    public IdempotencyResult(T result, boolean isDuplicate) {
        this(result, isDuplicate, false);
    }
    
    public IdempotencyResult(T result, boolean isDuplicate, boolean isInProgress) {
        this.result = result;
        this.isDuplicate = isDuplicate;
        this.isInProgress = isInProgress;
        this.isNewExecution = !isDuplicate;
        
        if (isInProgress) {
            this.status = IdempotencyStatus.IN_PROGRESS;
        } else if (isDuplicate) {
            this.status = IdempotencyStatus.COMPLETED;
        } else {
            this.status = IdempotencyStatus.COMPLETED;
        }
    }
    
    public boolean hasResult() {
        return result != null;
    }
}