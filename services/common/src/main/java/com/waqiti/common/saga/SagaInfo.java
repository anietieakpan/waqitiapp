package com.waqiti.common.saga;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Information about a saga execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInfo {
    
    private String sagaId;
    private String sagaType;
    private SagaStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private LocalDateTime completedAt;
    private int completedSteps;
    private int totalSteps;
    private Map<String, Object> sagaData;
    private String currentStepId;
    private List<String> failedSteps;
    private List<String> compensatedSteps;
    private long executionTimeMs;
    private String errorMessage;
    private String errorCode;
    private Map<String, Object> metadata;
    private String initiatedBy;
    private String correlationId;
    private Double progressPercentage;
    
    /**
     * Create saga info from basic parameters
     */
    public static SagaInfo create(String sagaId, String sagaType, SagaStatus status) {
        return SagaInfo.builder()
                .sagaId(sagaId)
                .sagaType(sagaType)
                .status(status)
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    /**
     * Constructor for backwards compatibility
     */
    public SagaInfo(String sagaId, String sagaType, SagaStatus status, 
                   LocalDateTime createdAt, LocalDateTime lastUpdated, 
                   int completedSteps, int totalSteps, Map<String, Object> sagaData) {
        this.sagaId = sagaId;
        this.sagaType = sagaType;
        this.status = status;
        this.createdAt = createdAt;
        this.lastUpdated = lastUpdated;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
        this.sagaData = sagaData;
        this.progressPercentage = calculateProgress();
    }
    
    /**
     * Calculate progress percentage
     */
    public Double calculateProgress() {
        if (totalSteps == 0) {
            return 0.0;
        }
        return (double) completedSteps / totalSteps * 100.0;
    }
    
    /**
     * Get progress percentage
     */
    public Double getProgressPercentage() {
        if (progressPercentage == null) {
            progressPercentage = calculateProgress();
        }
        return progressPercentage;
    }
    
    /**
     * Check if saga is completed
     */
    public boolean isCompleted() {
        return status == SagaStatus.COMPLETED;
    }
    
    /**
     * Check if saga has failed
     */
    public boolean isFailed() {
        return status == SagaStatus.FAILED || status == SagaStatus.COMPENSATION_FAILED;
    }
    
    /**
     * Check if saga is running
     */
    public boolean isRunning() {
        return status == SagaStatus.RUNNING || status == SagaStatus.COMPENSATING;
    }
    
    /**
     * Check if saga is in terminal state
     */
    public boolean isTerminal() {
        return status != null && (
            status == SagaStatus.COMPLETED ||
            status == SagaStatus.FAILED ||
            status == SagaStatus.CANCELLED ||
            status == SagaStatus.COMPENSATED ||
            status == SagaStatus.COMPENSATION_FAILED ||
            status == SagaStatus.TIMED_OUT
        );
    }
    
    /**
     * Get execution duration in milliseconds
     */
    public long getExecutionDurationMs() {
        if (executionTimeMs > 0) {
            return executionTimeMs;
        }
        
        if (createdAt != null && completedAt != null) {
            return java.time.Duration.between(createdAt, completedAt).toMillis();
        }
        
        if (createdAt != null && lastUpdated != null && !isRunning()) {
            return java.time.Duration.between(createdAt, lastUpdated).toMillis();
        }
        
        return 0;
    }
    
    /**
     * Update status and timestamp
     */
    public SagaInfo updateStatus(SagaStatus newStatus) {
        this.status = newStatus;
        this.lastUpdated = LocalDateTime.now();
        
        if (newStatus != null && (newStatus == SagaStatus.COMPLETED || 
                                 newStatus == SagaStatus.FAILED ||
                                 newStatus == SagaStatus.CANCELLED ||
                                 newStatus == SagaStatus.COMPENSATED ||
                                 newStatus == SagaStatus.COMPENSATION_FAILED)) {
            this.completedAt = LocalDateTime.now();
        }
        
        return this;
    }
    
    /**
     * Update progress
     */
    public SagaInfo updateProgress(int completedSteps, int totalSteps) {
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
        this.progressPercentage = calculateProgress();
        this.lastUpdated = LocalDateTime.now();
        return this;
    }
    
    /**
     * Add metadata
     */
    public SagaInfo withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    /**
     * Set error details
     */
    public SagaInfo withError(String errorMessage, String errorCode) {
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        return this;
    }
    
    /**
     * Set current step
     */
    public SagaInfo withCurrentStep(String currentStepId) {
        this.currentStepId = currentStepId;
        return this;
    }
}