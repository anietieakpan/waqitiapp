package com.waqiti.common.batch;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;

/**
 * Result of batch processing operation
 */
@Data
@Builder
public class BatchResult<T> {
    
    private String batchId;
    private int totalCount;
    private int successfulCount;
    private int failedCount;
    private List<T> results;
    private List<BatchError> errors;
    private Duration duration;
    
    public double getSuccessRate() {
        if (totalCount == 0) return 0.0;
        return (double) successfulCount / totalCount * 100;
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public boolean isSuccessful() {
        return failedCount == 0;
    }
    
    public String getSummary() {
        return String.format("Batch %s: %d/%d successful (%.1f%%) in %s", 
            batchId, successfulCount, totalCount, getSuccessRate(), 
            duration != null ? duration.toString() : "unknown");
    }
}