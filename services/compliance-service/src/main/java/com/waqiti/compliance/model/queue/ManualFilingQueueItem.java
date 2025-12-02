package com.waqiti.compliance.model.queue;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Manual Filing Queue Item Entity
 *
 * Represents a SAR/CTR/FBAR report that requires manual filing when
 * automated FinCEN submission fails.
 *
 * CRITICAL COMPLIANCE REQUIREMENT:
 * Reports must be filed within regulatory deadlines:
 * - SAR: 30 days from detection
 * - CTR: 15 days from transaction
 * - FBAR: April 15 (with extension)
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "manual_filing_queue", indexes = {
        @Index(name = "idx_manual_filing_status", columnList = "queue_status"),
        @Index(name = "idx_manual_filing_priority", columnList = "priority"),
        @Index(name = "idx_manual_filing_deadline", columnList = "filing_deadline"),
        @Index(name = "idx_manual_filing_type", columnList = "report_type"),
        @Index(name = "idx_manual_filing_assigned", columnList = "assigned_to")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_manual_filing_report_ref",
                columnNames = {"report_type", "report_reference_id"})
})
public class ManualFilingQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // Report identification
    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType; // SAR, CTR, FBAR

    @Column(name = "report_reference_id", nullable = false, length = 100)
    private String reportReferenceId;

    @Column(name = "original_report_id")
    private UUID originalReportId;

    // Filing details
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filing_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> filingData;

    @Column(name = "filing_format", length = 20)
    private String filingFormat = "XML";

    // Queue management
    @Column(name = "queue_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private QueueStatus queueStatus = QueueStatus.PENDING;

    @Column(name = "priority", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.NORMAL;

    // Deadline tracking
    @Column(name = "detection_date", nullable = false)
    private LocalDateTime detectionDate;

    @Column(name = "filing_deadline", nullable = false)
    private LocalDateTime filingDeadline;

    // Assignment
    @Column(name = "assigned_to", length = 255)
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    // Retry tracking
    @Column(name = "auto_filing_attempts", nullable = false)
    private Integer autoFilingAttempts = 0;

    @Column(name = "last_auto_attempt_at")
    private LocalDateTime lastAutoAttemptAt;

    @Column(name = "last_auto_error", columnDefinition = "TEXT")
    private String lastAutoError;

    @Column(name = "max_auto_retries")
    private Integer maxAutoRetries = 3;

    // Manual filing tracking
    @Column(name = "manual_filing_attempts")
    private Integer manualFilingAttempts = 0;

    @Column(name = "last_manual_attempt_at")
    private LocalDateTime lastManualAttemptAt;

    @Column(name = "last_manual_error", columnDefinition = "TEXT")
    private String lastManualError;

    // Filing completion
    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    @Column(name = "filed_by", length = 255)
    private String filedBy;

    @Column(name = "fincen_confirmation_number", length = 100)
    private String fincenConfirmationNumber;

    @Column(name = "fincen_tracking_id", length = 100)
    private String fincenTrackingId;

    @Column(name = "filing_receipt_url", length = 500)
    private String filingReceiptUrl;

    // Escalation
    @Column(name = "escalation_level")
    private Integer escalationLevel = 0;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_to", length = 255)
    private String escalatedTo;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    // Notifications
    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    @Column(name = "reminder_count")
    private Integer reminderCount = 0;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    // Source information
    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "source_transaction_ids", columnDefinition = "TEXT")
    private String sourceTransactionIds;

    // Audit fields
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy = "SYSTEM";

    @Version
    @Column(name = "version")
    private Long version = 1L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (queueStatus == null) {
            queueStatus = QueueStatus.PENDING;
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        calculatePriority();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate priority based on filing deadline
     */
    public void calculatePriority() {
        if (filingDeadline == null) return;

        long daysRemaining = java.time.Duration.between(
                LocalDateTime.now(), filingDeadline).toDays();

        if (daysRemaining < 0) {
            this.priority = Priority.CRITICAL;
        } else if (daysRemaining <= 7) {
            this.priority = Priority.HIGH;
        } else if (daysRemaining <= 14) {
            this.priority = Priority.NORMAL;
        } else {
            this.priority = Priority.LOW;
        }
    }

    /**
     * Check if this item is overdue
     */
    public boolean isOverdue() {
        return filingDeadline != null &&
                filingDeadline.isBefore(LocalDateTime.now()) &&
                queueStatus != QueueStatus.FILED &&
                queueStatus != QueueStatus.CANCELLED;
    }

    /**
     * Get days until deadline (negative if overdue)
     */
    public long getDaysUntilDeadline() {
        if (filingDeadline == null) return 0;
        return java.time.Duration.between(LocalDateTime.now(), filingDeadline).toDays();
    }

    /**
     * Queue status enumeration
     */
    public enum QueueStatus {
        PENDING,      // Waiting to be assigned
        ASSIGNED,     // Assigned to a compliance officer
        IN_PROGRESS,  // Being worked on
        FILED,        // Successfully filed
        FAILED,       // Failed after all attempts
        ESCALATED,    // Escalated to supervisor/manager
        CANCELLED     // Cancelled (e.g., duplicate)
    }

    /**
     * Priority enumeration
     */
    public enum Priority {
        CRITICAL,  // Overdue - immediate action required
        HIGH,      // Less than 7 days to deadline
        NORMAL,    // 7-14 days to deadline
        LOW        // More than 14 days to deadline
    }
}
