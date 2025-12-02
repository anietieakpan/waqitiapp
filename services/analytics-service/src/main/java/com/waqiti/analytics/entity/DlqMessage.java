package com.waqiti.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dead Letter Queue Message Entity
 *
 * Stores failed Kafka messages for recovery, analysis, and audit trail.
 * Supports retry logic, manual review workflow, and operational alerting.
 *
 * Lifecycle:
 * 1. PENDING_REVIEW - Initial state, awaiting automatic retry
 * 2. RETRY_IN_PROGRESS - Currently being retried
 * 3. RECOVERED - Successfully processed after retry
 * 4. FAILED - Max retries exceeded, needs manual intervention
 * 5. MANUAL_REVIEW_REQUIRED - Business logic requires human review
 * 6. ARCHIVED - Resolved and archived for audit purposes
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Entity
@Table(name = "dlq_messages", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_topic", columnList = "original_topic"),
    @Index(name = "idx_dlq_correlation_id", columnList = "correlation_id"),
    @Index(name = "idx_dlq_received_at", columnList = "received_at"),
    @Index(name = "idx_dlq_retry_eligible", columnList = "status, retry_count, last_retry_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"messageValue"})
@EqualsAndHashCode(of = "id")
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Original Kafka topic where message was consumed from
     */
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    /**
     * DLQ topic where failed message was sent
     */
    @Column(name = "dlq_topic", nullable = false, length = 255)
    private String dlqTopic;

    /**
     * Kafka partition number
     */
    @Column(name = "partition_number")
    private Integer partitionNumber;

    /**
     * Kafka offset
     */
    @Column(name = "offset_number")
    private Long offsetNumber;

    /**
     * Message key (usually entity ID)
     */
    @Column(name = "message_key", length = 500)
    private String messageKey;

    /**
     * Full message payload (JSON)
     */
    @Column(name = "message_value", columnDefinition = "TEXT", nullable = false)
    private String messageValue;

    /**
     * Kafka message headers (JSON map)
     */
    @Column(name = "headers", columnDefinition = "JSONB")
    private String headers;

    /**
     * Reason for failure
     */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /**
     * Stack trace of exception
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * Exception class name
     */
    @Column(name = "exception_class", length = 500)
    private String exceptionClass;

    /**
     * Current retry attempt count
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum retry attempts allowed (default 3)
     */
    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private Integer maxRetryAttempts = 3;

    /**
     * Timestamp of last retry attempt
     */
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    /**
     * Processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DlqStatus status = DlqStatus.PENDING_REVIEW;

    /**
     * Correlation ID for distributed tracing
     */
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    /**
     * Severity level for alerting
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    /**
     * Assigned reviewer for manual review
     */
    @Column(name = "assigned_to", length = 255)
    private String assignedTo;

    /**
     * Review notes from manual reviewer
     */
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    /**
     * Recovery action taken
     */
    @Column(name = "recovery_action", columnDefinition = "TEXT")
    private String recoveryAction;

    /**
     * Whether operations team was alerted
     */
    @Column(name = "alerted", nullable = false)
    @Builder.Default
    private Boolean alerted = false;

    /**
     * Timestamp when message was received in DLQ
     */
    @Column(name = "received_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime receivedAt;

    /**
     * Timestamp when message was processed/resolved
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking version
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * DLQ Message Status
     */
    public enum DlqStatus {
        PENDING_REVIEW,        // Awaiting automatic retry
        RETRY_IN_PROGRESS,     // Currently being retried
        RECOVERED,             // Successfully processed
        FAILED,                // Max retries exceeded
        MANUAL_REVIEW_REQUIRED,// Requires human intervention
        ARCHIVED               // Resolved and archived
    }

    /**
     * Severity levels for alerting
     */
    public enum Severity {
        LOW,     // Can wait for batch processing
        MEDIUM,  // Should be retried soon
        HIGH,    // Should be retried immediately
        CRITICAL // Requires immediate attention and alerting
    }

    /**
     * Check if message is eligible for retry
     */
    public boolean isEligibleForRetry() {
        return status == DlqStatus.PENDING_REVIEW &&
               retryCount < maxRetryAttempts;
    }

    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        this.status = DlqStatus.RETRY_IN_PROGRESS;
    }

    /**
     * Mark as recovered
     */
    public void markAsRecovered(String action) {
        this.status = DlqStatus.RECOVERED;
        this.recoveryAction = action;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Mark as failed after max retries
     */
    public void markAsFailed(String reason) {
        this.status = DlqStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Mark as requiring manual review
     */
    public void markAsManualReviewRequired(String reason) {
        this.status = DlqStatus.MANUAL_REVIEW_REQUIRED;
        this.failureReason = reason;
    }

    /**
     * Check if message is stale (pending for too long)
     */
    public boolean isStale(int hoursThreshold) {
        if (status != DlqStatus.PENDING_REVIEW) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(hoursThreshold);
        return receivedAt.isBefore(threshold);
    }
}
