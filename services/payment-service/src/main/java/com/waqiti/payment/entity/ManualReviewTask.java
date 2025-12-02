package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PRODUCTION ENTITY: Manual Review Task Tracking
 *
 * Tracks tasks requiring manual review by operations/finance teams for:
 * - Settlement failures requiring investigation
 * - High-value transaction anomalies
 * - Fraud alerts needing human verification
 * - Compliance exceptions
 * - Payment failures with unclear cause
 *
 * COMPLIANCE:
 * -----------
 * - SOX: Audit trail of manual interventions
 * - PSD2: Strong Customer Authentication exceptions
 * - BSA/AML: Suspicious activity review
 * - PCI-DSS: Security incident tracking
 *
 * WORKFLOW STATES:
 * ---------------
 * PENDING → ASSIGNED → IN_PROGRESS → RESOLVED/ESCALATED/CANCELLED
 *
 * @author Waqiti Production Team
 * @version 2.0.0
 * @since November 18, 2025
 */
@Entity
@Table(name = "manual_review_tasks", schema = "payment", indexes = {
    @Index(name = "idx_review_status", columnList = "status"),
    @Index(name = "idx_review_priority", columnList = "priority"),
    @Index(name = "idx_review_type", columnList = "review_type"),
    @Index(name = "idx_review_assigned_to", columnList = "assigned_to"),
    @Index(name = "idx_review_created_at", columnList = "created_at"),
    @Index(name = "idx_review_due_date", columnList = "due_date"),
    @Index(name = "idx_review_entity_id", columnList = "entity_type, entity_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Type of review task
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 50)
    private ReviewType reviewType;

    /**
     * Current status of review
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    /**
     * Priority level
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    /**
     * Type of entity being reviewed (PAYMENT, SETTLEMENT, TRANSACTION, USER)
     */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * ID of entity being reviewed
     */
    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    /**
     * Related payment ID (if applicable)
     */
    @Column(name = "payment_id", length = 50)
    private String paymentId;

    /**
     * Related settlement ID (if applicable)
     */
    @Column(name = "settlement_id", length = 50)
    private String settlementId;

    /**
     * Related batch ID (if applicable)
     */
    @Column(name = "batch_id", length = 50)
    private String batchId;

    /**
     * User ID being reviewed (if applicable)
     */
    @Column(name = "user_id", length = 50)
    private String userId;

    /**
     * Amount involved (for financial reviews)
     */
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Currency code
     */
    @Column(name = "currency", length = 3)
    private String currency;

    /**
     * Bank or institution code (if applicable)
     */
    @Column(name = "bank_code", length = 50)
    private String bankCode;

    /**
     * Title/summary of review task
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Detailed description of issue
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Reason for review (failure reason, alert trigger, etc.)
     */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /**
     * User/team assigned to review
     */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /**
     * When task was assigned
     */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /**
     * Due date for review completion
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * When task was started
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * When task was completed
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Resolution notes
     */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * Resolution action taken
     */
    @Column(name = "resolution_action", length = 100)
    private String resolutionAction;

    /**
     * User who resolved the task
     */
    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    /**
     * Number of escalations
     */
    @Column(name = "escalation_count")
    @Builder.Default
    private Integer escalationCount = 0;

    /**
     * Last escalation timestamp
     */
    @Column(name = "last_escalation_at")
    private LocalDateTime lastEscalationAt;

    /**
     * SLA compliance flag
     */
    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    /**
     * Timestamp of SLA breach
     */
    @Column(name = "sla_breached_at")
    private LocalDateTime slaBreachedAt;

    /**
     * Tags for categorization (JSON)
     */
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    // Audit fields
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ReviewStatus.PENDING;
        }
        if (priority == null) {
            priority = Priority.MEDIUM;
        }
        if (escalationCount == null) {
            escalationCount = 0;
        }
        if (slaBreached == null) {
            slaBreached = false;
        }
        // Set due date based on priority (if not set)
        if (dueDate == null && priority != null) {
            switch (priority) {
                case CRITICAL -> dueDate = createdAt.plusHours(2);   // 2 hours for critical
                case HIGH -> dueDate = createdAt.plusHours(4);       // 4 hours for high
                case MEDIUM -> dueDate = createdAt.plusHours(24);    // 24 hours for medium
                case LOW -> dueDate = createdAt.plusHours(72);       // 72 hours for low
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        // Check SLA breach
        if (dueDate != null && LocalDateTime.now().isAfter(dueDate) &&
            (status == ReviewStatus.PENDING || status == ReviewStatus.ASSIGNED || status == ReviewStatus.IN_PROGRESS)) {
            if (!Boolean.TRUE.equals(slaBreached)) {
                slaBreached = true;
                slaBreachedAt = LocalDateTime.now();
            }
        }
    }

    /**
     * Review Type Enum
     */
    public enum ReviewType {
        SETTLEMENT_FAILURE,
        PAYMENT_FAILURE,
        FRAUD_ALERT,
        COMPLIANCE_EXCEPTION,
        HIGH_VALUE_TRANSACTION,
        SUSPICIOUS_ACTIVITY,
        KYC_VERIFICATION,
        CHARGEBACK,
        DISPUTE,
        REFUND_REQUEST,
        ACCOUNT_ANOMALY,
        TRANSACTION_ANOMALY,
        OTHER
    }

    /**
     * Review Status Enum
     */
    public enum ReviewStatus {
        PENDING,        // Awaiting assignment
        ASSIGNED,       // Assigned to reviewer
        IN_PROGRESS,    // Under review
        RESOLVED,       // Completed successfully
        ESCALATED,      // Escalated to higher authority
        CANCELLED,      // Cancelled/no longer needed
        ON_HOLD         // Temporarily paused
    }

    /**
     * Priority Enum
     */
    public enum Priority {
        CRITICAL,   // Requires immediate attention (< 2 hours)
        HIGH,       // Urgent (< 4 hours)
        MEDIUM,     // Normal priority (< 24 hours)
        LOW         // Can wait (< 72 hours)
    }

    /**
     * Check if task is overdue
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate) &&
                (status == ReviewStatus.PENDING || status == ReviewStatus.ASSIGNED || status == ReviewStatus.IN_PROGRESS);
    }

    /**
     * Assign task to user/team
     */
    public void assign(String assignee) {
        this.assignedTo = assignee;
        this.assignedAt = LocalDateTime.now();
        this.status = ReviewStatus.ASSIGNED;
    }

    /**
     * Start working on task
     */
    public void start() {
        this.startedAt = LocalDateTime.now();
        this.status = ReviewStatus.IN_PROGRESS;
    }

    /**
     * Resolve task
     */
    public void resolve(String resolvedByUser, String action, String notes) {
        this.resolvedBy = resolvedByUser;
        this.resolutionAction = action;
        this.resolutionNotes = notes;
        this.completedAt = LocalDateTime.now();
        this.status = ReviewStatus.RESOLVED;
    }

    /**
     * Escalate task
     */
    public void escalate() {
        this.escalationCount++;
        this.lastEscalationAt = LocalDateTime.now();
        this.status = ReviewStatus.ESCALATED;

        // Upgrade priority on escalation
        if (priority == Priority.LOW) {
            priority = Priority.MEDIUM;
        } else if (priority == Priority.MEDIUM) {
            priority = Priority.HIGH;
        } else if (priority == Priority.HIGH) {
            priority = Priority.CRITICAL;
        }
    }
}
