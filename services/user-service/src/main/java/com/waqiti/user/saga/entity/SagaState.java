package com.waqiti.user.saga.entity;

import com.waqiti.user.saga.SagaStatus;
import com.waqiti.user.saga.SagaType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga State Entity
 *
 * Tracks distributed transaction state for saga pattern implementation
 * Provides complete audit trail and enables compensation on failure
 */
@Entity
@Table(name = "saga_states", indexes = {
        @Index(name = "idx_saga_status", columnList = "status"),
        @Index(name = "idx_saga_type", columnList = "saga_type"),
        @Index(name = "idx_saga_created_at", columnList = "created_at"),
        @Index(name = "idx_saga_active", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaState {

    @Id
    @Column(name = "saga_id", length = 100)
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_type", nullable = false, length = 50)
    private SagaType sagaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SagaStatus status;

    @Column(name = "completed_steps", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private List<SagaStep> completedSteps = new ArrayList<>();

    @Column(name = "request_data", columnDefinition = "TEXT")
    private String requestData;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "compensation_count", nullable = false)
    @Builder.Default
    private Integer compensationCount = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensation_started_at")
    private LocalDateTime compensationStartedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Add completed step to saga
     */
    public void addCompletedStep(String stepName, String stepResult, String description) {
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        }

        SagaStep step = SagaStep.builder()
                .stepName(stepName)
                .stepResult(stepResult)
                .description(description)
                .completedAt(LocalDateTime.now())
                .compensated(false)
                .compensationFailed(false)
                .build();

        completedSteps.add(step);
    }

    /**
     * Get total duration in milliseconds
     */
    public long getDurationMs() {
        if (completedAt == null) {
            return java.time.Duration.between(createdAt, LocalDateTime.now()).toMillis();
        }
        return java.time.Duration.between(createdAt, completedAt).toMillis();
    }

    /**
     * Check if saga is in terminal state
     */
    public boolean isTerminal() {
        return status == SagaStatus.COMPLETED ||
               status == SagaStatus.COMPENSATED ||
               status == SagaStatus.COMPENSATION_FAILED ||
               status == SagaStatus.FAILED;
    }
}
