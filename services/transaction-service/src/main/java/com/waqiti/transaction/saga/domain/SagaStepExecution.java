package com.waqiti.transaction.saga.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL: Individual saga step execution state
 * Tracks execution of each step for precise crash recovery
 */
@Entity
@Table(name = "saga_step_executions", indexes = {
    @Index(name = "idx_step_saga_id", columnList = "saga_id"),
    @Index(name = "idx_step_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "step_execution_id")
    private UUID stepExecutionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaExecution sagaExecution;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;  // JSON serialized input

    @Column(name = "output_data", columnDefinition = "TEXT")
    private String outputData;  // JSON serialized output

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "compensation_data", columnDefinition = "TEXT")
    private String compensationData;  // Data needed for rollback

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensated_at")
    private LocalDateTime compensatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum StepStatus {
        PENDING,       // Not yet started
        RUNNING,       // Currently executing
        COMPLETED,     // Successfully completed
        FAILED,        // Failed to execute
        COMPENSATING,  // Rolling back
        COMPENSATED,   // Successfully rolled back
        SKIPPED        // Skipped due to previous failure
    }

    /**
     * Mark step as started
     */
    public void start() {
        this.status = StepStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Mark step as completed
     */
    public void complete(String output) {
        this.status = StepStatus.COMPLETED;
        this.outputData = output;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark step as failed
     */
    public void fail(String error) {
        this.status = StepStatus.FAILED;
        this.errorMessage = error;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Start compensation for this step
     */
    public void startCompensation() {
        this.status = StepStatus.COMPENSATING;
    }

    /**
     * Mark step as compensated
     */
    public void compensate() {
        this.status = StepStatus.COMPENSATED;
        this.compensatedAt = LocalDateTime.now();
    }

    /**
     * Skip this step
     */
    public void skip() {
        this.status = StepStatus.SKIPPED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count
     */
    public void incrementRetry() {
        this.retryCount++;
    }
}
