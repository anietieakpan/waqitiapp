package com.waqiti.common.async;

import lombok.Builder;
import lombok.Data;

/**
 * Status tracking for async tasks
 */
@Data
@Builder
public class AsyncTaskStatus {
    
    private String taskId;
    private String operationName;
    private TaskStatus status;
    private int progress;
    private long startTime;
    private Long endTime;
    private String error;
    
    public long getExecutionTime() {
        if (endTime != null) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    public boolean isComplete() {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }
    
    public boolean isRunning() {
        return status == TaskStatus.RUNNING;
    }
    
    public String getFormattedProgress() {
        return progress >= 0 ? progress + "%" : "N/A";
    }
}

enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}