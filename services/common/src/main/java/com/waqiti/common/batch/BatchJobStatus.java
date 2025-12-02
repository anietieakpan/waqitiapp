package com.waqiti.common.batch;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Status tracking for batch jobs
 */
@Data
@Builder
public class BatchJobStatus {
    
    private String batchId;
    private String batchName;
    private int totalItems;
    private int processedItems;
    private int successfulItems;
    private int failedItems;
    private BatchStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String error;
    private double progress;
    
    @Builder.Default
    private List<Object> failedItemsList = new ArrayList<>();
    
    public Duration getExecutionTime() {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return Duration.between(startTime, end);
    }
    
    public boolean isComplete() {
        return status != null && status.isComplete();
    }
    
    public String getProgressPercentage() {
        if (totalItems == 0) return "0%";
        return String.format("%.1f%%", progress);
    }
    
    // Alias method for compatibility
    public void setErrorMessage(String errorMessage) {
        this.error = errorMessage;
    }
}