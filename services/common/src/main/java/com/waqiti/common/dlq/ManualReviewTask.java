package com.waqiti.common.dlq;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * ✅ CRITICAL PRODUCTION FIX: Manual Review Task Entity
 *
 * Tracks failed Kafka events that require human intervention for recovery.
 * Part of comprehensive DLQ recovery framework addressing 240+ empty DLQ handlers.
 *
 * BUSINESS IMPACT:
 * - Prevents permanent data loss from failed events
 * - Enables systematic recovery of financial transactions
 * - Provides audit trail for regulatory compliance
 * - Supports operational workflows for finance team
 *
 * INTEGRATION POINTS:
 * - DLQ consumers create tasks when automatic recovery fails
 * - Manual review UI dashboard displays pending tasks
 * - Workflow engine routes tasks to appropriate teams
 * - Alerting system notifies stakeholders
 * - Metrics service tracks recovery SLAs
 *
 * @author Production Readiness Team
 * @since 2025-11-04
 */
@Entity
@Table(name = "manual_review_tasks", indexes = {
    @Index(name = "idx_status_priority", columnList = "status,priority"),
    @Index(name = "idx_assigned_team", columnList = "assigned_team"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_topic", columnList = "topic")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id", updatable = false, nullable = false)
    private UUID taskId;

    /**
     * Kafka topic from which the event originated
     */
    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    /**
     * Kafka partition number
     */
    @Column(name = "partition_num", nullable = false)
    private Integer partition;

    /**
     * Kafka offset within partition
     */
    @Column(name = "offset_num", nullable = false)
    private Long offset;

    /**
     * Kafka message key (for event identification)
     */
    @Column(name = "event_key", length = 500)
    private String eventKey;

    /**
     * Full Kafka message value (JSON payload)
     * Stored as TEXT to handle large payloads
     */
    @Column(name = "event_value", columnDefinition = "TEXT")
    private String eventValue;

    /**
     * Reason why automatic recovery failed
     */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /**
     * Stack trace of error (for debugging)
     */
    @Column(name = "error_stacktrace", columnDefinition = "TEXT")
    private String errorStacktrace;

    /**
     * Number of automatic retry attempts before DLQ
     */
    @Column(name = "retry_count")
    private Integer retryCount;

    /**
     * Task priority based on business impact
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority;

    /**
     * Current status of manual review task
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TaskStatus status;

    /**
     * Team responsible for reviewing this task
     */
    @Column(name = "assigned_team", length = 100)
    private String assignedTeam;

    /**
     * User ID of person assigned to review (if any)
     */
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    /**
     * When task was created
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * When task was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * When task was resolved (completed or rejected)
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * User who resolved the task
     */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /**
     * Resolution notes/comments
     */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * SLA deadline for this task (based on priority)
     */
    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    /**
     * Whether SLA was breached
     */
    @Column(name = "sla_breached")
    private Boolean slaBreached;

    /**
     * PagerDuty incident ID (if escalated)
     */
    @Column(name = "pagerduty_incident_id", length = 100)
    private String pagerdutyIncidentId;

    /**
     * JIRA ticket key (if created)
     */
    @Column(name = "jira_ticket_key", length = 50)
    private String jiraTicketKey;

    /**
     * Additional context/metadata (stored as JSON)
     */
    @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
    @Column(name = "metadata", columnDefinition = "JSONB")
    private Map<String, Object> metadata;

    /**
     * Service name that produced the event
     */
    @Column(name = "source_service", length = 100)
    private String sourceService;

    /**
     * Service name that failed to consume the event
     */
    @Column(name = "target_service", length = 100)
    private String targetService;

    /**
     * Business entity affected (e.g., "PAYMENT", "WALLET", "TRANSACTION")
     */
    @Column(name = "business_entity_type", length = 50)
    private String businessEntityType;

    /**
     * Business entity ID (e.g., payment ID, wallet ID)
     */
    @Column(name = "business_entity_id")
    private UUID businessEntityId;

    /**
     * Estimated financial impact (if applicable)
     */
    @Column(name = "financial_impact", precision = 19, scale = 4)
    private java.math.BigDecimal financialImpact;

    /**
     * Currency code for financial impact
     */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    /**
     * Task priority levels based on business impact
     */
    public enum TaskPriority {
        CRITICAL,  // P0: Financial integrity issues, requires immediate attention
        HIGH,      // P1: Important business operations, 24-hour SLA
        MEDIUM,    // P2: Standard operations, 72-hour SLA
        LOW        // P3: Non-critical, 7-day SLA
    }

    /**
     * Task status lifecycle
     */
    public enum TaskStatus {
        PENDING_REVIEW,        // Awaiting manual review
        IN_REVIEW,             // Currently being reviewed
        AWAITING_INFORMATION,  // Need more context to resolve
        RESOLVED_SUCCESS,      // Successfully recovered/fixed
        RESOLVED_DUPLICATE,    // Duplicate of another event
        RESOLVED_REJECTED,     // Event invalid/should be discarded
        ESCALATED              // Escalated to engineering/management
    }

    /**
     * Pre-persist callback to set timestamps and calculate SLA
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (slaDeadline == null) {
            slaDeadline = calculateSlaDeadline();
        }
        if (slaBreached == null) {
            slaBreached = false;
        }
    }

    /**
     * Pre-update callback to update timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        // Check SLA breach
        if (slaDeadline != null && LocalDateTime.now().isAfter(slaDeadline)) {
            slaBreached = true;
        }

        // Set resolution timestamp if status changed to resolved
        if (isResolved() && resolvedAt == null) {
            resolvedAt = LocalDateTime.now();
        }
    }

    /**
     * Calculate SLA deadline based on priority
     */
    private LocalDateTime calculateSlaDeadline() {
        return switch (priority) {
            case CRITICAL -> createdAt.plusHours(1);   // 1 hour
            case HIGH -> createdAt.plusHours(24);      // 24 hours
            case MEDIUM -> createdAt.plusHours(72);    // 3 days
            case LOW -> createdAt.plusDays(7);         // 7 days
        };
    }

    /**
     * Check if task is in a resolved state
     */
    public boolean isResolved() {
        return status == TaskStatus.RESOLVED_SUCCESS ||
               status == TaskStatus.RESOLVED_DUPLICATE ||
               status == TaskStatus.RESOLVED_REJECTED;
    }

    /**
     * Check if SLA is about to be breached (within 10% of deadline)
     */
    public boolean isSlaAtRisk() {
        if (slaDeadline == null || isResolved()) {
            return false;
        }

        long totalMinutes = java.time.Duration.between(createdAt, slaDeadline).toMinutes();
        long remainingMinutes = java.time.Duration.between(LocalDateTime.now(), slaDeadline).toMinutes();

        return remainingMinutes < (totalMinutes * 0.1); // Less than 10% time remaining
    }

    /**
     * Get age of task in hours
     */
    public long getAgeInHours() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
    }
}
