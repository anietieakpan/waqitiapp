package com.waqiti.saga.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single step within a SAGA transaction.
 * Each step has an action to execute and a compensation action for rollback.
 */
@Entity
@Table(name = "saga_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_transaction_id", nullable = false)
    private SagaTransaction sagaTransaction;

    @Column(nullable = false)
    private Integer stepIndex;

    @Column(nullable = false)
    private String stepName;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String actionEndpoint;

    @Column
    private String compensationEndpoint;

    @Column(columnDefinition = "jsonb")
    private String actionPayload;

    @Column(columnDefinition = "jsonb")
    private String compensationPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    @Column(columnDefinition = "jsonb")
    private String response;

    @Column
    private String errorMessage;

    @Column
    private Integer retryCount;

    @Column
    private Integer maxRetries;

    @Column
    private LocalDateTime executedAt;

    @Column
    private LocalDateTime compensatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum StepStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        COMPENSATING,
        COMPENSATED,
        SKIPPED
    }

    /**
     * Marks the step as completed
     */
    public void markCompleted(String response) {
        this.status = StepStatus.COMPLETED;
        this.response = response;
        this.executedAt = LocalDateTime.now();
    }

    /**
     * Marks the step as failed
     */
    public void markFailed(String errorMessage) {
        this.status = StepStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Marks the step as compensated
     */
    public void markCompensated() {
        this.status = StepStatus.COMPENSATED;
        this.compensatedAt = LocalDateTime.now();
    }

    /**
     * Increments retry count
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
    }

    /**
     * Checks if max retries exceeded
     */
    public boolean isMaxRetriesExceeded() {
        return maxRetries != null && retryCount != null && retryCount >= maxRetries;
    }

    /**
     * Checks if step can be retried
     */
    public boolean canRetry() {
        return status == StepStatus.FAILED && !isMaxRetriesExceeded();
    }

    /**
     * Checks if step requires compensation
     */
    public boolean requiresCompensation() {
        return status == StepStatus.COMPLETED && compensationEndpoint != null;
    }
}