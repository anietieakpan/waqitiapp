package com.waqiti.reconciliation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_jobs", indexes = {
    @Index(name = "idx_reconciliation_jobs_date_type", columnList = "reconciliation_date, job_type"),
    @Index(name = "idx_reconciliation_jobs_status", columnList = "status"),
    @Index(name = "idx_reconciliation_jobs_started_at", columnList = "started_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationJob {

    @Id
    @Column(name = "job_id")
    @Type(type = "uuid-char")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    @NotNull
    private JobType jobType;

    @Column(name = "reconciliation_date", nullable = false)
    @NotNull
    private LocalDate reconciliationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @NotNull
    private com.waqiti.reconciliation.model.ReconciliationStatus status;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "reconciliation_job_steps", joinColumns = @JoinColumn(name = "job_id"))
    @MapKeyColumn(name = "step_name", length = 100)
    @Column(name = "step_result", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, Object> stepResults = new HashMap<>();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum JobType {
        END_OF_DAY("End of Day Reconciliation"),
        REAL_TIME("Real Time Reconciliation"),
        NOSTRO_RECONCILIATION("Nostro Account Reconciliation"),
        SETTLEMENT_RECONCILIATION("Settlement Reconciliation"),
        AD_HOC("Ad Hoc Reconciliation"),
        BATCH_RECON("Batch Reconciliation"),
        VARIANCE_INVESTIGATION("Variance Investigation");

        private final String description;

        JobType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public void addStepResult(String stepName, Object result) {
        if (stepResults == null) {
            stepResults = new HashMap<>();
        }
        stepResults.put(stepName, result);
    }

    public void markCompleted() {
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = com.waqiti.reconciliation.model.ReconciliationStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean isFailed() {
        return com.waqiti.reconciliation.model.ReconciliationStatus.FAILED.equals(status);
    }
}