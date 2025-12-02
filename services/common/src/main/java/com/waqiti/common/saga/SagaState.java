package com.waqiti.common.saga;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistent state of a saga execution.
 * Stores the current status, step states, and data for recovery purposes.
 */
@Entity
@Table(name = "saga_states")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaState {
    
    @Id
    private String sagaId;
    
    @Column(nullable = false)
    private String sagaType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;
    
    @Column(name = "saga_data", columnDefinition = "TEXT")
    @Convert(converter = MapJsonConverter.class)
    @Builder.Default
    private Map<String, Object> sagaData = new HashMap<>();
    
    @Column(name = "step_states", columnDefinition = "TEXT")
    @Convert(converter = StepStatesJsonConverter.class)
    @Builder.Default
    private Map<String, StepState> stepStates = new HashMap<>();
    
    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdated;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "compensation_data", columnDefinition = "TEXT")
    @Convert(converter = MapJsonConverter.class)
    @Builder.Default
    private Map<String, Object> compensationData = new HashMap<>();
    
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;
    
    @Column(name = "timeout_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timeoutAt;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "priority")
    @Builder.Default
    private int priority = 0;
    
    @ElementCollection
    @CollectionTable(name = "saga_tags", joinColumns = @JoinColumn(name = "saga_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    public SagaState(String sagaId, String sagaType, SagaStatus status, 
                     Map<String, Object> sagaData, LocalDateTime createdAt) {
        this.sagaId = sagaId;
        this.sagaType = sagaType;
        this.status = status;
        this.sagaData = sagaData != null ? sagaData : new HashMap<>();
        this.createdAt = createdAt;
        this.lastUpdated = createdAt;
    }
    
    /**
     * Update step state
     */
    public void updateStepState(String stepId, StepState stepState) {
        this.stepStates.put(stepId, stepState);
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Get step state
     */
    public StepState getStepState(String stepId) {
        return stepStates.get(stepId);
    }
    
    /**
     * Add saga data
     */
    public void addSagaData(String key, Object value) {
        this.sagaData.put(key, value);
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Add saga data from map
     */
    public void addSagaData(Map<String, Object> data) {
        if (data != null) {
            this.sagaData.putAll(data);
            this.lastUpdated = LocalDateTime.now();
        }
    }
    
    /**
     * Get saga data value
     */
    public Object getSagaData(String key) {
        return sagaData.get(key);
    }
    
    /**
     * Add compensation data
     */
    public void addCompensationData(String key, Object value) {
        this.compensationData.put(key, value);
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Set error message and update status
     */
    public void setError(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = SagaStatus.FAILED;
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Mark as completed
     */
    public void markCompleted() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Mark as compensated
     */
    public void markCompensated() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Check if max retries exceeded
     */
    public boolean isMaxRetriesExceeded() {
        return retryCount >= maxRetries;
    }
    
    /**
     * Check if saga is timed out
     */
    public boolean isTimedOut() {
        return timeoutAt != null && LocalDateTime.now().isAfter(timeoutAt);
    }
    
    /**
     * Check if saga is in a terminal state
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }
    
    /**
     * Get completed steps count
     */
    public long getCompletedStepsCount() {
        return stepStates.values().stream()
            .filter(state -> state.getStatus() == StepStatus.COMPLETED)
            .count();
    }
    
    /**
     * Get failed steps count
     */
    public long getFailedStepsCount() {
        return stepStates.values().stream()
            .filter(state -> state.getStatus() == StepStatus.FAILED)
            .count();
    }
    
    /**
     * Get running steps count
     */
    public long getRunningStepsCount() {
        return stepStates.values().stream()
            .filter(state -> state.getStatus() == StepStatus.RUNNING)
            .count();
    }
    
    /**
     * Get pending steps count
     */
    public long getPendingStepsCount() {
        return stepStates.values().stream()
            .filter(state -> state.getStatus() == StepStatus.PENDING)
            .count();
    }
    
    /**
     * Get all failed steps
     */
    public List<StepState> getFailedSteps() {
        return stepStates.values().stream()
            .filter(state -> state.getStatus() == StepStatus.FAILED)
            .collect(Collectors.toList());
    }
    
    /**
     * Get saga duration in milliseconds
     */
    public long getDurationMs() {
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(createdAt, endTime).toMillis();
    }
    
    /**
     * Add tag
     */
    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new HashSet<>();
        }
        this.tags.add(tag);
    }
    
    /**
     * Remove tag
     */
    public void removeTag(String tag) {
        if (this.tags != null) {
            this.tags.remove(tag);
        }
    }
    
    /**
     * Check if has tag
     */
    public boolean hasTag(String tag) {
        return this.tags != null && this.tags.contains(tag);
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        lastUpdated = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Get saga version (using retry count as version indicator)
     */
    public int getVersion() {
        return retryCount;
    }
    
    /**
     * Get metadata (returns saga data)
     */
    public Map<String, Object> getMetadata() {
        return sagaData;
    }
}