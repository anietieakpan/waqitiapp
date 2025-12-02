package com.waqiti.support.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Dead Letter Queue message entity for tracking and recovering failed Kafka messages.
 * Addresses BLOCKER-003: Unimplemented DLQ recovery logic.
 *
 * This entity stores failed messages for:
 * - Manual review and intervention
 * - Automatic retry with backoff
 * - Audit trail and compliance
 * - Operational monitoring and alerting
 */
@Entity
@Table(name = "dlq_messages", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_topic", columnList = "original_topic"),
    @Index(name = "idx_dlq_received", columnList = "received_at"),
    @Index(name = "idx_dlq_next_retry", columnList = "next_retry_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * Original Kafka topic where the message failed
     */
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    /**
     * Kafka partition where the message failed
     */
    @Column(name = "partition_id")
    private Integer partitionId;

    /**
     * Kafka offset of the failed message
     */
    @Column(name = "offset_value")
    private Long offset;

    /**
     * Original message payload (JSON or serialized format)
     */
    @Column(name = "message_payload", nullable = false, columnDefinition = "TEXT")
    private String messagePayload;

    /**
     * Message key if present
     */
    @Column(name = "message_key", length = 500)
    private String messageKey;

    /**
     * Number of retry attempts made
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum retry attempts before marking as PERMANENT_FAILURE
     */
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    /**
     * Current status of the DLQ message
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DlqStatus status = DlqStatus.PENDING_REVIEW;

    /**
     * Error message or exception details
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Full stack trace of the error
     */
    @Column(name = "error_stacktrace", columnDefinition = "TEXT")
    private String errorStacktrace;

    /**
     * When the message was received in DLQ
     */
    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    /**
     * When the next automatic retry should occur
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /**
     * When the message was last processed/retried
     */
    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    /**
     * When the message was resolved or permanently failed
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * User or system that resolved the message
     */
    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    /**
     * Additional metadata or context (JSON format)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Priority level for manual review
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    /**
     * Whether operations team has been alerted
     */
    @Column(name = "alert_sent", nullable = false)
    @Builder.Default
    private Boolean alertSent = false;

    /**
     * Notes from manual review or intervention
     */
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    /**
     * Audit: Created timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Audit: Last updated timestamp
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Increments retry count and calculates next retry time with exponential backoff
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastProcessedAt = Instant.now();

        if (this.retryCount >= this.maxRetries) {
            this.status = DlqStatus.PERMANENT_FAILURE;
            this.resolvedAt = Instant.now();
        } else {
            this.status = DlqStatus.RETRY_SCHEDULED;
            // Exponential backoff: 2^retryCount minutes
            long backoffMinutes = (long) Math.pow(2, this.retryCount);
            this.nextRetryAt = Instant.now().plusSeconds(backoffMinutes * 60);
        }
    }

    /**
     * Marks message as successfully recovered
     */
    public void markRecovered(String resolvedBy) {
        this.status = DlqStatus.RECOVERED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
    }

    /**
     * Marks message as permanently failed
     */
    public void markPermanentFailure(String reason) {
        this.status = DlqStatus.PERMANENT_FAILURE;
        this.resolvedAt = Instant.now();
        this.reviewNotes = (this.reviewNotes != null ? this.reviewNotes + "\n" : "") + reason;
    }

    /**
     * DLQ message status enum
     */
    public enum DlqStatus {
        /** Newly received, awaiting review */
        PENDING_REVIEW,

        /** Under manual investigation */
        UNDER_INVESTIGATION,

        /** Scheduled for automatic retry */
        RETRY_SCHEDULED,

        /** Currently being retried */
        RETRY_IN_PROGRESS,

        /** Successfully recovered and reprocessed */
        RECOVERED,

        /** Failed all retry attempts */
        PERMANENT_FAILURE,

        /** Manually archived/ignored */
        ARCHIVED
    }

    /**
     * Priority for manual review
     */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
