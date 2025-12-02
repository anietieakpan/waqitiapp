package com.waqiti.common.saga;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * State of an individual step in the saga
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepState {
    
    @Column(name = "step_id")
    private String stepId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StepStatus status;
    
    @Column(name = "started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "result_data", columnDefinition = "TEXT")
    @Convert(converter = MapJsonConverter.class)
    @Builder.Default
    private Map<String, Object> resultData = new HashMap<>();
    
    @Column(name = "compensation_data", columnDefinition = "TEXT")
    @Convert(converter = MapJsonConverter.class)
    @Builder.Default
    private Map<String, Object> compensationData = new HashMap<>();
    
    @Transient
    private StepResult result;
    
    public StepState(String stepId, StepStatus status) {
        this.stepId = stepId;
        this.status = status;
    }
    
    /**
     * Mark step as started
     */
    public void markStarted() {
        this.status = StepStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }
    
    /**
     * Mark step as completed
     */
    public void markCompleted(StepResult result) {
        this.status = StepStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.result = result;
        if (result != null && result.getAllData() != null) {
            this.resultData.putAll(result.getAllData());
        }
    }
    
    /**
     * Mark step as failed
     */
    public void markFailed(String errorMessage) {
        this.status = StepStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
    
    /**
     * Mark step as compensated
     */
    public void markCompensated() {
        this.status = StepStatus.COMPENSATED;
    }
    
    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * Get step duration in milliseconds
     */
    public long getDurationMs() {
        if (startedAt == null) return 0;
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, endTime).toMillis();
    }
    
    /**
     * Check if step is terminal
     */
    public boolean isTerminal() {
        return status == StepStatus.COMPLETED || 
               status == StepStatus.FAILED || 
               status == StepStatus.COMPENSATED;
    }
}