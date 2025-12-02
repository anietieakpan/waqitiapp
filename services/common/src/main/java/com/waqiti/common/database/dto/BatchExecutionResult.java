package com.waqiti.common.database.dto;

import lombok.Data;

/**
 * Result of batch database operations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class BatchExecutionResult {
    private int totalOperations;
    private int successCount;
    private int failureCount;
    private long executionTimeMs;
    
    public BatchExecutionResult() {}
    
    public BatchExecutionResult(int totalOperations, int failureCount, long executionTimeMs) {
        this.totalOperations = totalOperations;
        this.failureCount = failureCount;
        this.successCount = totalOperations - failureCount;
        this.executionTimeMs = executionTimeMs;
    }
}