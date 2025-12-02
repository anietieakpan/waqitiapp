package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Employee Training Record
 *
 * Tracks individual training module completions by employees.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "employee_training_records", indexes = {
        @Index(name = "idx_training_record_employee", columnList = "employee_id"),
        @Index(name = "idx_training_record_module", columnList = "module_id"),
        @Index(name = "idx_training_record_status", columnList = "status"),
        @Index(name = "idx_training_record_completed", columnList = "completed_at")
})
@Data
@Builder(builderMethodName = "internalBuilder")
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeTrainingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "record_id")
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeIdField;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, insertable = false, updatable = false)
    private Employee employee;

    @Column(name = "module_id", nullable = false)
    private UUID moduleIdField;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false, insertable = false, updatable = false)
    private SecurityTrainingModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TrainingStatus status = TrainingStatus.NOT_STARTED;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "score")
    private Integer score;

    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "certificate_issued")
    @Builder.Default
    private Boolean certificateIssued = false;

    @Column(name = "certificate_url", length = 500)
    private String certificateUrl;

    @Column(name = "attempts")
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts_allowed")
    @Builder.Default
    private Integer maxAttemptsAllowed = 3;

    @Column(name = "score_percentage")
    private Integer scorePercentage;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "certificate_issued_at")
    private LocalDateTime certificateIssuedAt;

    @Column(name = "certificate_expires_at")
    private LocalDateTime certificateExpiresAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledgment_signature", length = 500)
    private String acknowledgmentSignature;

    @Column(name = "acknowledgment_ip_address", length = 45)
    private String acknowledgmentIpAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum TrainingStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    /**
     * Custom builder that accepts employeeId and moduleId as UUIDs
     */
    public static EmployeeTrainingRecordBuilder builder() {
        return internalBuilder();
    }

    public static class EmployeeTrainingRecordBuilder {
        public EmployeeTrainingRecordBuilder employeeId(UUID employeeId) {
            this.employeeIdField = employeeId;
            return this;
        }

        public EmployeeTrainingRecordBuilder moduleId(UUID moduleId) {
            this.moduleIdField = moduleId;
            return this;
        }
    }

    /**
     * Get employee ID
     */
    public UUID getEmployeeId() {
        return this.employeeIdField;
    }

    /**
     * Get module ID
     */
    public UUID getModuleId() {
        return this.moduleIdField;
    }

    /**
     * Mark training as started
     */
    public void startTraining() {
        this.status = TrainingStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Complete training with score
     */
    public void completeTraining(Integer score, boolean passed) {
        this.status = passed ? TrainingStatus.COMPLETED : TrainingStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.score = score;
        this.passed = passed;

        if (this.startedAt != null) {
            this.durationMinutes = (int) java.time.Duration
                    .between(this.startedAt, this.completedAt)
                    .toMinutes();
        }
    }
}