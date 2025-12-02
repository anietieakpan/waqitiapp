package com.waqiti.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operational Task Entity
 *
 * Represents manual operational tasks requiring human intervention.
 * Created when automated processes fail or require manual decision-making.
 *
 * TASK LIFECYCLE:
 * PENDING -> ASSIGNED -> IN_PROGRESS -> COMPLETED/ESCALATED/CANCELLED
 *
 * PRIORITY LEVELS & SLAs:
 * - CRITICAL: 15 minutes SLA (emergency manual freeze, critical system failure)
 * - HIGH: 1 hour SLA (manual transaction review, compliance escalation)
 * - MEDIUM: 4 hours SLA (reconciliation discrepancies, customer escalations)
 * - LOW: 24 hours SLA (routine manual reviews)
 *
 * COMMON TASK TYPES:
 * - EMERGENCY_MANUAL_FREEZE: System unable to freeze account automatically
 * - MANUAL_TRANSACTION_REVIEW: Transaction flagged for manual review
 * - RECONCILIATION_FIX: Reconciliation discrepancy requiring manual resolution
 * - COMPLIANCE_REVIEW: Manual compliance review required
 * - CUSTOMER_ESCALATION: High-priority customer issue
 *
 * @author Waqiti Operations Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "operational_tasks")
public class OperationalTask {

    /**
     * Unique task identifier
     */
    @Id
    private String id;

    /**
     * Task type classification
     */
    @Indexed
    private String type;

    /**
     * Task priority level
     */
    @Indexed
    private Priority priority;

    /**
     * User ID associated with task (if applicable)
     */
    @Indexed
    private UUID userId;

    /**
     * Detailed task description
     */
    private String description;

    /**
     * Reason for manual intervention
     */
    private String freezeReason;

    /**
     * Incident severity
     */
    private String severity;

    /**
     * Associated case ID for tracking
     */
    @Indexed
    private String caseId;

    /**
     * SLA in minutes
     */
    private Integer slaMinutes;

    /**
     * Task creation timestamp
     */
    @Indexed
    private LocalDateTime createdAt;

    /**
     * Task due timestamp (based on SLA)
     */
    @Indexed
    private LocalDateTime dueAt;

    /**
     * Task status
     * Values: PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, ESCALATED, CANCELLED
     */
    @Indexed
    private String status;

    /**
     * Assigned to operator
     */
    @Indexed
    private String assignedTo;

    /**
     * When task was assigned
     */
    private LocalDateTime assignedAt;

    /**
     * When work started on task
     */
    private LocalDateTime startedAt;

    /**
     * When task was completed
     */
    private LocalDateTime completedAt;

    /**
     * Task outcome/result
     */
    private String outcome;

    /**
     * When task was escalated
     */
    private LocalDateTime escalatedAt;

    /**
     * Escalation reason
     */
    private String escalationReason;

    /**
     * Cancellation reason (if cancelled)
     */
    private String cancellationReason;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Additional notes
     */
    private String notes;

    /**
     * Task metadata (JSON)
     */
    private String metadata;

    /**
     * Priority enum
     */
    public enum Priority {
        CRITICAL,   // 15 min SLA
        HIGH,       // 1 hour SLA
        MEDIUM,     // 4 hours SLA
        LOW         // 24 hours SLA
    }

    /**
     * Check if task is overdue
     */
    public boolean isOverdue() {
        if (dueAt == null) return false;
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            return false;
        }
        return LocalDateTime.now().isAfter(dueAt);
    }

    /**
     * Check if task is critical
     */
    public boolean isCritical() {
        return Priority.CRITICAL.equals(priority);
    }

    /**
     * Get minutes until due
     */
    public long getMinutesUntilDue() {
        if (dueAt == null) return Long.MAX_VALUE;
        return java.time.Duration.between(LocalDateTime.now(), dueAt).toMinutes();
    }

    /**
     * Get total processing time (from creation to completion)
     */
    public long getProcessingTimeMinutes() {
        if (createdAt == null || completedAt == null) return 0;
        return java.time.Duration.between(createdAt, completedAt).toMinutes();
    }
}
