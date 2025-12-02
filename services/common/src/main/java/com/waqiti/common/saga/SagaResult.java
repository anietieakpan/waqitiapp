package com.waqiti.common.saga;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of a saga execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaResult {
    
    private String sagaId;
    private SagaStatus status;
    private String message;
    private Map<String, Object> resultData;
    private long executionTimeMs;
    private LocalDateTime completedAt;
    private String errorCode;
    private Throwable error;
    private int stepsCompleted;
    private int totalSteps;
    
    /**
     * Create a successful result
     */
    public static SagaResult success(String sagaId, Map<String, Object> resultData, long executionTimeMs) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(SagaStatus.COMPLETED)
                .message("Saga completed successfully")
                .resultData(resultData)
                .executionTimeMs(executionTimeMs)
                .completedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a failure result
     */
    public static SagaResult failure(String sagaId, String message, String errorCode) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(SagaStatus.FAILED)
                .message(message)
                .errorCode(errorCode)
                .completedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a running result
     */
    public static SagaResult running(String sagaId) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(SagaStatus.RUNNING)
                .message("Saga is currently running")
                .build();
    }
    
    /**
     * Create a cancelled result
     */
    public static SagaResult cancelled(String sagaId, String reason) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(SagaStatus.CANCELLED)
                .message("Saga was cancelled: " + reason)
                .completedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a compensated result
     */
    public static SagaResult compensated(String sagaId) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(SagaStatus.COMPENSATED)
                .message("Saga was compensated successfully")
                .completedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Check if the saga completed successfully
     */
    public boolean isSuccess() {
        return status == SagaStatus.COMPLETED;
    }
    
    /**
     * Check if the saga failed
     */
    public boolean isFailure() {
        return status == SagaStatus.FAILED || status == SagaStatus.COMPENSATION_FAILED;
    }
    
    /**
     * Check if the saga is still running
     */
    public boolean isRunning() {
        return status == SagaStatus.RUNNING || status == SagaStatus.COMPENSATING;
    }
    
    /**
     * Check if the saga is in a terminal state
     */
    public boolean isTerminal() {
        return status != null && (status == SagaStatus.COMPLETED || 
                                 status == SagaStatus.FAILED ||
                                 status == SagaStatus.CANCELLED ||
                                 status == SagaStatus.COMPENSATED ||
                                 status == SagaStatus.COMPENSATION_FAILED ||
                                 status == SagaStatus.TIMED_OUT);
    }
}

/**
 * Constructor for backwards compatibility
 */
class SagaResultConstructor {
    public static SagaResult create(String sagaId, SagaStatus status, String message) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(status)
                .message(message)
                .build();
    }
    
    public static SagaResult create(String sagaId, SagaStatus status, String message, 
                                   Map<String, Object> resultData, long executionTimeMs) {
        return SagaResult.builder()
                .sagaId(sagaId)
                .status(status)
                .message(message)
                .resultData(resultData)
                .executionTimeMs(executionTimeMs)
                .completedAt(LocalDateTime.now())
                .build();
    }
}