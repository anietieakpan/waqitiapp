package com.waqiti.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a message requiring manual operations team review
 *
 * <p>Tracks messages that cannot be automatically recovered and require
 * human intervention. Prioritized based on business impact.</p>
 *
 * <h3>Priority Levels:</h3>
 * <ul>
 *   <li>CRITICAL - First account failures, immediate attention (15min SLA)</li>
 *   <li>HIGH - Financial impact, urgent (1hr SLA)</li>
 *   <li>MEDIUM - Standard issues (4hr SLA)</li>
 *   <li>LOW - Minor issues (24hr SLA)</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Entity
@Table(name = "dlq_manual_review_queue", indexes = {
    @Index(name = "idx_manual_review_status", columnList = "status, priority"),
    @Index(name = "idx_manual_review_priority", columnList = "priority, created_at"),
    @Index(name = "idx_manual_review_created", columnList = "created_at"),
    @Index(name = "idx_manual_review_assigned", columnList = "assigned_to"),
    @Index(name = "idx_manual_review_sla", columnList = "sla_due_at"),
    @Index(name = "idx_manual_review_topic", columnList = "original_topic"),
    @Index(name = "idx_manual_review_correlation", columnList = "correlation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"payload", "exceptionStackTrace", "contextData"})
public class ManualReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Original Message Metadata
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    @Column(name = "original_partition", nullable = false)
    private Integer originalPartition;

    @Column(name = "original_offset", nullable = false)
    private Long originalOffset;

    @Column(name = "original_key", length = 500)
    private String originalKey;

    // Message Payload (sanitized - PII masked)
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    // Error Information
    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Column(name = "exception_class", length = 500)
    private String exceptionClass;

    @Column(name = "exception_stack_trace", columnDefinition = "TEXT")
    private String exceptionStackTrace;

    // Failure Metadata
    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "retry_attempts", nullable = false)
    @Builder.Default
    private Integer retryAttempts = 0;

    // Review Information
    @Column(name = "review_reason", nullable = false, length = 500)
    private String reviewReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private ReviewPriority priority = ReviewPriority.MEDIUM;

    // Assignment and Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "assigned_to", length = 255)
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    // Resolution
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolution_action", length = 100)
    private String resolutionAction;

    // SLA Tracking
    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    // Audit Fields
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", length = 255)
    @Builder.Default
    private String createdBy = "system";

    // Handler Information
    @Column(name = "handler_name", nullable = false, length = 255)
    private String handlerName;

    // Correlation for Tracing
    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    // Additional Context (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", columnDefinition = "jsonb")
    private Map<String, Object> contextData;

    /**
     * Review priority enumeration
     */
    public enum ReviewPriority {
        CRITICAL,  // 15min SLA - first account failures
        HIGH,      // 1hr SLA - financial impact
        MEDIUM,    // 4hr SLA - standard issues
        LOW        // 24hr SLA - minor issues
    }

    /**
     * Review status enumeration
     */
    public enum ReviewStatus {
        PENDING,      // Awaiting review
        IN_REVIEW,    // Currently being reviewed
        RESOLVED,     // Successfully resolved
        ESCALATED,    // Escalated to higher level
        DISMISSED     // Dismissed as non-issue
    }

    /**
     * Pre-persist callback - auto-calculate SLA
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ReviewStatus.PENDING;
        }
        if (priority == null) {
            priority = ReviewPriority.MEDIUM;
        }
        if (retryAttempts == null) {
            retryAttempts = 0;
        }
        if (slaBreached == null) {
            slaBreached = false;
        }
        if (createdBy == null) {
            createdBy = "system";
        }

        // Auto-calculate SLA deadline if not set
        if (slaDueAt == null) {
            slaDueAt = calculateSlaDueDate(createdAt, priority);
        }
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate SLA due date based on priority
     */
    private LocalDateTime calculateSlaDueDate(LocalDateTime createdAt, ReviewPriority priority) {
        return switch (priority) {
            case CRITICAL -> createdAt.plusMinutes(15);
            case HIGH -> createdAt.plusHours(1);
            case MEDIUM -> createdAt.plusHours(4);
            case LOW -> createdAt.plusHours(24);
        };
    }

    /**
     * Assign to reviewer
     */
    public void assignTo(String reviewer) {
        this.assignedTo = reviewer;
        this.assignedAt = LocalDateTime.now();
        this.status = ReviewStatus.IN_REVIEW;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark as resolved
     */
    public void resolve(String resolvedBy, String notes, String action) {
        this.status = ReviewStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.resolutionAction = action;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Escalate to higher priority
     */
    public void escalate(String reason) {
        this.status = ReviewStatus.ESCALATED;
        this.resolutionNotes = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Dismiss as non-issue
     */
    public void dismiss(String dismissedBy, String reason) {
        this.status = ReviewStatus.DISMISSED;
        this.resolvedBy = dismissedBy;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNotes = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if SLA is breached
     */
    public boolean isSlaBreached() {
        if (slaBreached != null && slaBreached) {
            return true;
        }
        return slaDueAt != null &&
               LocalDateTime.now().isAfter(slaDueAt) &&
               (status == ReviewStatus.PENDING || status == ReviewStatus.IN_REVIEW);
    }

    /**
     * Mark SLA as breached
     */
    public void markSlaBreached() {
        this.slaBreached = true;
        this.updatedAt = LocalDateTime.now();
    }
}
