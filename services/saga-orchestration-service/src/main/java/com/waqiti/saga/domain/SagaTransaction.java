package com.waqiti.saga.domain;

import com.waqiti.common.saga.SagaStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a distributed transaction managed by the SAGA pattern.
 * Each saga transaction consists of multiple steps that must be executed
 * in sequence, with compensation actions available for rollback.
 */
@Entity
@Table(name = "saga_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String sagaType;

    @Column(nullable = false)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    @Column(columnDefinition = "jsonb")
    private String contextData;

    @OneToMany(mappedBy = "sagaTransaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SagaStep> steps = new ArrayList<>();

    @Column
    private String failureReason;

    @Column
    private Integer currentStepIndex;

    @Column
    private Integer retryCount;

    @Column
    private Integer maxRetries;

    @Column
    private LocalDateTime timeoutAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;


    /**
     * Adds a step to the saga transaction
     */
    public void addStep(SagaStep step) {
        step.setSagaTransaction(this);
        step.setStepIndex(this.steps.size());
        this.steps.add(step);
    }

    /**
     * Gets the current executing step
     */
    public SagaStep getCurrentStep() {
        if (currentStepIndex == null || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    /**
     * Advances to the next step
     */
    public boolean advanceToNextStep() {
        if (currentStepIndex == null) {
            currentStepIndex = 0;
        } else {
            currentStepIndex++;
        }
        return currentStepIndex < steps.size();
    }

    /**
     * Checks if all steps are completed
     */
    public boolean isCompleted() {
        return steps.stream().allMatch(step -> step.getStatus() == SagaStep.StepStatus.COMPLETED);
    }

    /**
     * Checks if the saga has timed out
     */
    public boolean isTimedOut() {
        return timeoutAt != null && LocalDateTime.now().isAfter(timeoutAt);
    }

    /**
     * Gets steps that need compensation (in reverse order)
     */
    public List<SagaStep> getStepsToCompensate() {
        return steps.stream()
                .filter(step -> step.getStatus() == SagaStep.StepStatus.COMPLETED)
                .sorted((a, b) -> Integer.compare(b.getStepIndex(), a.getStepIndex()))
                .toList();
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
}