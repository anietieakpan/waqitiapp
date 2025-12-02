package com.waqiti.common.gdpr.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Data Deletion Result Entity
 *
 * Implements GDPR Article 17 (Right to Erasure - "Right to be Forgotten").
 * Tracks data deletion requests and maintains audit trail for compliance.
 *
 * Two-phase deletion:
 * 1. Anonymization: Personal data is anonymized
 * 2. Hard deletion: Records are physically deleted after retention period
 *
 * Exceptions handled:
 * - Legal hold requirements
 * - Financial transaction retention (7 years)
 * - Compliance obligations
 */
@Entity
@Table(name = "gdpr_data_deletions", indexes = {
    @Index(name = "idx_deletion_user_id", columnList = "user_id"),
    @Index(name = "idx_deletion_status", columnList = "status"),
    @Index(name = "idx_deletion_requested", columnList = "requested_at"),
    @Index(name = "idx_deletion_completed", columnList = "completed_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRDataDeletionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "deletion_request_id", unique = true, nullable = false, length = 100)
    private String deletionRequestId;

    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DeletionStatus status;

    @NotNull
    @Column(name = "deletion_reason", nullable = false, length = 500)
    private String deletionReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    @Column(name = "hard_deleted_at")
    private LocalDateTime hardDeletedAt;

    @Column(name = "verification_hash", length = 128)
    private String verificationHash; // Hash to verify deletion

    @Column(name = "total_records_deleted")
    private Integer totalRecordsDeleted = 0;

    @Column(name = "total_records_anonymized")
    private Integer totalRecordsAnonymized = 0;

    @Column(name = "total_records_retained")
    private Integer totalRecordsRetained = 0;

    @Column(name = "retention_exceptions", columnDefinition = "JSONB")
    private String retentionExceptions; // Records that must be retained (legal, financial)

    @Column(name = "deleted_entities", columnDefinition = "JSONB")
    private String deletedEntities; // List of entity types deleted

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "requested_by", length = 100)
    private String requestedBy; // USER_SELF, ADMIN, LEGAL_TEAM, AUTOMATED

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DeletionStatus {
        PENDING_APPROVAL,   // Awaiting approval
        APPROVED,           // Approved, ready to process
        PROCESSING,         // Deletion in progress
        ANONYMIZED,         // Data anonymized
        COMPLETED,          // Fully deleted
        PARTIAL,            // Partially deleted (some data retained)
        FAILED,             // Deletion failed
        CANCELLED,          // Request cancelled
        ON_LEGAL_HOLD       // Cannot delete due to legal hold
    }

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DeletionStatus.PENDING_APPROVAL;
        }
        if (deletionRequestId == null) {
            deletionRequestId = "DEL-" + UUID.randomUUID().toString();
        }
    }

    /**
     * Mark as completed
     */
    public void markCompleted(int deleted, int anonymized, int retained) {
        this.status = retained > 0 ? DeletionStatus.PARTIAL : DeletionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.totalRecordsDeleted = deleted;
        this.totalRecordsAnonymized = anonymized;
        this.totalRecordsRetained = retained;
    }

    /**
     * Check if deletion is complete
     */
    public boolean isComplete() {
        return status == DeletionStatus.COMPLETED || status == DeletionStatus.PARTIAL;
    }
}
