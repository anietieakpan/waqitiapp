package com.waqiti.transaction.saga.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CRITICAL: Persistent saga execution state for crash recovery
 * Ensures sagas can be resumed or compensated after service restarts
 */
@Entity
@Table(name = "saga_executions", indexes = {
    @Index(name = "idx_saga_status", columnList = "status"),
    @Index(name = "idx_saga_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_saga_created_at", columnList = "created_at"),
    @Index(name = "idx_saga_last_updated", columnList = "last_updated_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaExecution {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "saga_type", nullable = false, length = 100)
    private String sagaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SagaStatus status;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps;

    @OneToMany(mappedBy = "sagaExecution", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepNumber ASC")
    @Builder.Default
    private List<SagaStepExecution> steps = new ArrayList<>();

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;  // JSON serialized saga context

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum SagaStatus {
        PENDING,        // Created but not started
        RUNNING,        // Currently executing
        COMPENSATING,   // Rolling back due to failure
        COMPLETED,      // Successfully completed
        COMPENSATED,    // Successfully rolled back
        FAILED,         // Failed and cannot compensate
        TIMEOUT         // Exceeded timeout duration
    }

    /**
     * Mark saga as started
     */
    public void start() {
        this.status = SagaStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Mark saga step as completed
     */
    public void completeStep(int stepNumber, String result) {
        this.currentStep = stepNumber;
        this.lastUpdatedAt = LocalDateTime.now();

        SagaStepExecution step = steps.stream()
            .filter(s -> s.getStepNumber() == stepNumber)
            .findFirst()
            .orElse(null);

        if (step != null) {
            step.complete(result);
        }
    }

    /**
     * Mark saga step as failed
     */
    public void failStep(int stepNumber, String error) {
        this.currentStep = stepNumber;
        this.errorMessage = error;
        this.lastUpdatedAt = LocalDateTime.now();

        SagaStepExecution step = steps.stream()
            .filter(s -> s.getStepNumber() == stepNumber)
            .findFirst()
            .orElse(null);

        if (step != null) {
            step.fail(error);
        }
    }

    /**
     * Start compensation (rollback)
     */
    public void startCompensation() {
        this.status = SagaStatus.COMPENSATING;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Mark saga as successfully completed
     */
    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Mark saga as compensated (successfully rolled back)
     */
    public void compensate() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Mark saga as failed (cannot compensate)
     */
    public void fail(String error) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = error;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Mark saga as timed out
     */
    public void timeout() {
        this.status = SagaStatus.TIMEOUT;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Check if saga has exceeded max retries
     */
    public boolean hasExceededRetries() {
        return this.retryCount >= this.maxRetries;
    }

    /**
     * Check if saga has timed out
     */
    public boolean hasTimedOut() {
        return this.timeoutAt != null && LocalDateTime.now().isAfter(this.timeoutAt);
    }

    /**
     * Get completed steps count
     */
    public long getCompletedStepsCount() {
        return steps.stream()
            .filter(s -> s.getStatus() == SagaStepExecution.StepStatus.COMPLETED)
            .count();
    }

    /**
     * Check if all steps are completed
     */
    public boolean areAllStepsCompleted() {
        return getCompletedStepsCount() == totalSteps;
    }

    /**
     * Add step to saga
     */
    public void addStep(SagaStepExecution step) {
        step.setSagaExecution(this);
        this.steps.add(step);
    }
}
