package com.waqiti.common.saga;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of a saga step execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    
    private String stepId;
    private StepStatus status;
    private String message;
    private Map<String, Object> data;
    private String errorMessage;
    private String errorCode;
    private Throwable error;
    private LocalDateTime timestamp;
    private long executionTime;
    private int retryCount;
    private boolean retryable;
    private Map<String, Object> metadata;
    
    /**
     * Create a successful step result
     */
    public static StepResult success(String stepId, Map<String, Object> data) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.COMPLETED)
                .message("Step completed successfully")
                .data(data)
                .timestamp(LocalDateTime.now())
                .retryable(false)
                .build();
    }
    
    /**
     * Create a successful step result with execution time
     */
    public static StepResult success(String stepId, Map<String, Object> data, long executionTime) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.COMPLETED)
                .message("Step completed successfully")
                .data(data)
                .executionTime(executionTime)
                .timestamp(LocalDateTime.now())
                .retryable(false)
                .build();
    }
    
    /**
     * Create a failed step result
     */
    public static StepResult failure(String stepId, String errorMessage) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.FAILED)
                .message("Step execution failed")
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .retryable(true)
                .build();
    }
    
    /**
     * Create a failed step result with error code
     */
    public static StepResult failure(String stepId, String errorMessage, String errorCode) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.FAILED)
                .message("Step execution failed")
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .retryable(true)
                .build();
    }
    
    /**
     * Create a failed step result with exception
     */
    public static StepResult failure(String stepId, String errorMessage, Throwable error) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.FAILED)
                .message("Step execution failed")
                .errorMessage(errorMessage)
                .error(error)
                .timestamp(LocalDateTime.now())
                .retryable(true)
                .build();
    }
    
    /**
     * Create a retryable failure result
     */
    public static StepResult retryableFailure(String stepId, String errorMessage, int retryCount) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.FAILED)
                .message("Step execution failed, will retry")
                .errorMessage(errorMessage)
                .retryCount(retryCount)
                .timestamp(LocalDateTime.now())
                .retryable(true)
                .build();
    }
    
    /**
     * Create a non-retryable failure result
     */
    public static StepResult nonRetryableFailure(String stepId, String errorMessage, String errorCode) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.FAILED)
                .message("Step execution failed, not retryable")
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .retryable(false)
                .build();
    }
    
    /**
     * Create a skipped step result
     */
    public static StepResult skipped(String stepId, String reason) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.SKIPPED)
                .message("Step was skipped: " + reason)
                .timestamp(LocalDateTime.now())
                .retryable(false)
                .build();
    }
    
    /**
     * Create a pending step result
     */
    public static StepResult pending(String stepId) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.PENDING)
                .message("Step is pending execution")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a running step result
     */
    public static StepResult running(String stepId) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.RUNNING)
                .message("Step is currently running")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a compensated step result
     */
    public static StepResult compensated(String stepId) {
        return StepResult.builder()
                .stepId(stepId)
                .status(StepStatus.COMPENSATED)
                .message("Step was compensated successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Check if the step was successful
     */
    public boolean isSuccess() {
        return status == StepStatus.COMPLETED;
    }
    
    /**
     * Check if the step failed
     */
    public boolean isFailure() {
        return status == StepStatus.FAILED;
    }
    
    /**
     * Check if the step is retryable
     */
    public boolean canRetry() {
        return retryable && status == StepStatus.FAILED;
    }
    
    /**
     * Add metadata to the result
     */
    public StepResult withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    /**
     * Add execution timing
     */
    public StepResult withTiming(long executionTime) {
        this.executionTime = executionTime;
        return this;
    }
    
    /**
     * Set attempt number for retries
     */
    public StepResult withAttempt(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }
    
    /**
     * Get all data (alias method for compatibility)
     */
    public Map<String, Object> getAllData() {
        return data != null ? data : new java.util.HashMap<>();
    }
}