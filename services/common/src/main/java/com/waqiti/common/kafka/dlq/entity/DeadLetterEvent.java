package com.waqiti.common.kafka.dlq.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Dead Letter Event Entity
 *
 * Stores Kafka events that have failed processing and moved to DLQ (Dead Letter Queue).
 * Enables event replay, investigation, and recovery.
 *
 * Features:
 * - Full event payload preservation (JSON)
 * - Failure reason tracking
 * - Retry count tracking
 * - Status lifecycle (NEW → RETRYING → RESOLVED/FAILED)
 * - Event metadata (topic, partition, offset)
 * - Automatic timestamping
 *
 * Use Cases:
 * - Failed event investigation
 * - Manual event replay
 * - Automated retry mechanisms
 * - Compliance audit trail
 * - Error pattern analysis
 *
 * @author Waqiti Platform - Event Architecture Team
 * @version 1.0
 * @since 2025-10-11
 */
@Entity
@Table(name = "dead_letter_events",
    indexes = {
        @Index(name = "idx_dle_event_id", columnList = "eventId", unique = true),
        @Index(name = "idx_dle_event_type", columnList = "eventType"),
        @Index(name = "idx_dle_status", columnList = "status"),
        @Index(name = "idx_dle_created_at", columnList = "createdAt"),
        @Index(name = "idx_dle_service", columnList = "serviceName"),
        @Index(name = "idx_dle_topic", columnList = "topic"),
        @Index(name = "idx_dle_retry", columnList = "retryCount")
    })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "payload")
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Original event ID from the failed event
     * Used for idempotency and correlation
     */
    @Column(nullable = false, unique = true, length = 255)
    private String eventId;

    /**
     * Event type (fully qualified class name)
     * E.g., "com.waqiti.payment.events.PaymentCompletedEvent"
     */
    @Column(nullable = false, length = 500)
    private String eventType;

    /**
     * Service that failed to process the event
     */
    @Column(nullable = false, length = 100)
    private String serviceName;

    /**
     * Consumer class that failed
     */
    @Column(length = 500)
    private String consumerClass;

    /**
     * Kafka topic name
     */
    @Column(nullable = false, length = 255)
    private String topic;

    /**
     * Kafka partition
     */
    @Column
    private Integer partition;

    /**
     * Kafka offset
     */
    @Column
    private Long offset;

    /**
     * Original event payload (JSON)
     * Stored as CLOB to support large events
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Failure reason (exception message)
     */
    @Column(nullable = false, length = 2000)
    private String failureReason;

    /**
     * Stack trace (for debugging)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * Number of retry attempts
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum retry attempts allowed
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * Status of the DLQ event
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DLQStatus status = DLQStatus.NEW;

    /**
     * Severity level
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DLQSeverity severity = DLQSeverity.MEDIUM;

    /**
     * Tags for categorization (comma-separated)
     * E.g., "payment,critical,fraud-detection"
     */
    @Column(length = 500)
    private String tags;

    /**
     * Resolution notes (manual intervention)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * Resolved by (user ID or system)
     */
    @Column(length = 100)
    private String resolvedBy;

    /**
     * Timestamp when event was created in DLQ
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of last retry attempt
     */
    @Column
    private LocalDateTime lastRetryAt;

    /**
     * Timestamp when resolved or permanently failed
     */
    @Column
    private LocalDateTime resolvedAt;

    /**
     * Next scheduled retry time
     */
    @Column
    private LocalDateTime nextRetryAt;

    /**
     * Expiration time (auto-delete after this)
     */
    @Column
    private LocalDateTime expiresAt;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    // Enum definitions

    public enum DLQStatus {
        /**
         * Newly created, awaiting processing
         */
        NEW,

        /**
         * Currently being retried
         */
        RETRYING,

        /**
         * Successfully resolved (retry succeeded)
         */
        RESOLVED,

        /**
         * Permanently failed (max retries exceeded)
         */
        FAILED,

        /**
         * Manually marked as resolved
         */
        MANUALLY_RESOLVED,

        /**
         * Skipped (not worth retrying)
         */
        SKIPPED,

        /**
         * Under investigation
         */
        INVESTIGATING
    }

    public enum DLQSeverity {
        /**
         * Low severity - informational failures
         */
        LOW,

        /**
         * Medium severity - standard failures
         */
        MEDIUM,

        /**
         * High severity - important business events failed
         */
        HIGH,

        /**
         * Critical severity - financial transaction failures
         */
        CRITICAL
    }

    // Helper methods

    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries &&
               (this.status == DLQStatus.NEW || this.status == DLQStatus.RETRYING);
    }

    public void markResolved(String resolvedBy, String notes) {
        this.status = DLQStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = notes;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = DLQStatus.FAILED;
        this.failureReason = reason;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markSkipped(String reason) {
        this.status = DLQStatus.SKIPPED;
        this.resolutionNotes = reason;
        this.resolvedAt = LocalDateTime.now();
    }

    public boolean isFinalized() {
        return this.status == DLQStatus.RESOLVED ||
               this.status == DLQStatus.FAILED ||
               this.status == DLQStatus.MANUALLY_RESOLVED ||
               this.status == DLQStatus.SKIPPED;
    }

    public boolean isExpired() {
        return this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt);
    }
}
