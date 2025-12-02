package com.waqiti.saga.domain;

import com.waqiti.common.saga.SagaStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Saga Execution Entity
 * 
 * Represents a running instance of a saga transaction.
 * Tracks the current state, completed steps, and overall progress.
 */
@Entity
@Table(name = "saga_executions", indexes = {
    @Index(name = "idx_saga_id", columnList = "sagaId", unique = true),
    @Index(name = "idx_saga_type", columnList = "sagaType"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_correlation_id", columnList = "correlationId")
})
@EntityListeners(AuditingEntityListener.class)
public class SagaExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotNull
    @Column(nullable = false, unique = true)
    private String sagaId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaType sagaType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    @Column
    private String correlationId;

    @Column
    private String initiatedBy;

    // Current step being executed
    @Column
    private String currentStep;

    @Column
    private Integer currentStepIndex = 0;

    @Column
    private Integer totalSteps;

    // Saga execution context (stored as JSON)
    @Column(columnDefinition = "TEXT")
    private String executionContext;

    // Result of the saga execution
    @Column(columnDefinition = "TEXT")
    private String executionResult;

    // Error information
    @Column(length = 1000)
    private String errorMessage;

    @Column
    private String errorCode;

    @Column
    private String failedStep;

    // Timing information
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime failedAt;

    @Column
    private LocalDateTime timeoutAt;

    // Retry information
    @Column
    private Integer retryCount = 0;

    @Column
    private Integer maxRetries = 3;

    @Column
    private LocalDateTime nextRetryAt;

    // Version for optimistic locking
    @Version
    private Long version;

    // Saga steps
    @OneToMany(mappedBy = "sagaExecution", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("stepIndex ASC")
    private List<SagaStep> steps = new ArrayList<>();

    // Constructors
    public SagaExecution() {
        this.status = SagaStatus.INITIATED;
        this.retryCount = 0;
    }

    public SagaExecution(String sagaId, SagaType sagaType, String correlationId) {
        this();
        this.sagaId = sagaId;
        this.sagaType = sagaType;
        this.correlationId = correlationId;
    }

    // Business methods
    public void start() {
        this.status = SagaStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.currentStepIndex = 0;
    }

    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.currentStep = null;
    }

    public void fail(String errorMessage, String errorCode, String failedStep) {
        this.status = SagaStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.failedStep = failedStep;
    }

    public void compensate() {
        this.status = SagaStatus.COMPENSATING;
    }

    public void compensated() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
    }

    public void timeout() {
        this.status = SagaStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.errorMessage = "Saga execution timed out";
        this.errorCode = "TIMEOUT";
    }

    public void moveToNextStep(String stepName) {
        this.currentStep = stepName;
        this.currentStepIndex++;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        // Exponential backoff
        long delayMinutes = (long) Math.pow(2, retryCount);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }

    public boolean canRetry() {
        return retryCount < maxRetries && 
               status == SagaStatus.FAILED;
    }

    public boolean isTerminal() {
        return status == SagaStatus.COMPLETED || 
               status == SagaStatus.COMPENSATED || 
               (status == SagaStatus.FAILED && !canRetry());
    }

    public boolean isActive() {
        return status == SagaStatus.RUNNING || 
               status == SagaStatus.COMPENSATING;
    }

    public double getProgressPercentage() {
        if (totalSteps == null || totalSteps == 0) {
            return 0.0;
        }
        return (currentStepIndex.doubleValue() / totalSteps.doubleValue()) * 100.0;
    }

    public void addStep(SagaStep step) {
        step.setSagaExecution(this);
        this.steps.add(step);
    }

    // Context manipulation methods
    @Transient
    private Map<String, Object> contextMap;

    public Map<String, Object> getContextMap() {
        if (contextMap == null) {
            contextMap = new HashMap<>();
            // Parse JSON context if available
            // Implementation would use Jackson ObjectMapper
        }
        return contextMap;
    }

    public void setContextValue(String key, Object value) {
        getContextMap().put(key, value);
        // Serialize back to JSON
        // Implementation would use Jackson ObjectMapper
    }

    public Object getContextValue(String key) {
        return getContextMap().get(key);
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public SagaType getSagaType() {
        return sagaType;
    }

    public void setSagaType(SagaType sagaType) {
        this.sagaType = sagaType;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStatus status) {
        this.status = status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public Integer getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(Integer currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public Integer getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(Integer totalSteps) {
        this.totalSteps = totalSteps;
    }

    public String getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(String executionContext) {
        this.executionContext = executionContext;
    }

    public String getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getFailedStep() {
        return failedStep;
    }

    public void setFailedStep(String failedStep) {
        this.failedStep = failedStep;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }

    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<SagaStep> getSteps() {
        return steps;
    }

    public void setSteps(List<SagaStep> steps) {
        this.steps = steps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SagaExecution that = (SagaExecution) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SagaExecution{" +
               "id='" + id + '\'' +
               ", sagaId='" + sagaId + '\'' +
               ", sagaType=" + sagaType +
               ", status=" + status +
               ", currentStep='" + currentStep + '\'' +
               ", createdAt=" + createdAt +
               '}';
    }
}