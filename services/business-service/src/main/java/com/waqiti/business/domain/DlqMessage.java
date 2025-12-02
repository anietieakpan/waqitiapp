package com.waqiti.business.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Dead Letter Queue Message Entity
 *
 * Persists failed Kafka messages for manual intervention and automated retry.
 * This ensures no financial data is lost due to transient failures.
 *
 * CRITICAL: Financial service - all failed messages must be recoverable
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-01-16
 */
@Entity
@Table(name = "dlq_messages", indexes = {
        @Index(name = "idx_dlq_messages_consumer", columnList = "consumer_name"),
        @Index(name = "idx_dlq_messages_status", columnList = "status"),
        @Index(name = "idx_dlq_messages_created", columnList = "created_at"),
        @Index(name = "idx_dlq_messages_retry_after", columnList = "retry_after"),
        @Index(name = "idx_dlq_messages_topic", columnList = "original_topic")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DlqMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "original_topic", nullable = false, length = 200)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "message_key", length = 500)
    private String messageKey;

    @Type(type = "jsonb")
    @Column(name = "message_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> messagePayload;

    @Type(type = "jsonb")
    @Column(name = "headers", columnDefinition = "jsonb")
    private Map<String, Object> headers;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private DlqStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 5;

    @Column(name = "retry_after")
    private LocalDateTime retryAfter;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "processing_notes", columnDefinition = "TEXT")
    private String processingNotes;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DlqStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 5;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count and calculate next retry time using exponential backoff
     *
     * @return true if retries remaining, false if max retries exceeded
     */
    public boolean incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();

        if (this.retryCount >= this.maxRetries) {
            this.status = DlqStatus.MAX_RETRIES_EXCEEDED;
            return false;
        }

        // Exponential backoff: 2^retryCount minutes, capped at 1 day
        int backoffMinutes = (int) Math.min(Math.pow(2, this.retryCount), 1440);
        this.retryAfter = LocalDateTime.now().plusMinutes(backoffMinutes);
        this.status = DlqStatus.RETRY_SCHEDULED;

        return true;
    }

    /**
     * Mark message for immediate retry
     */
    public void scheduleImmediateRetry() {
        this.retryAfter = LocalDateTime.now();
        this.status = DlqStatus.RETRY_SCHEDULED;
    }

    /**
     * Mark message as successfully recovered
     */
    public void markRecovered(String resolvedBy) {
        this.status = DlqStatus.RECOVERED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Mark message as requiring manual intervention
     */
    public void markManualIntervention(String notes) {
        this.status = DlqStatus.MANUAL_INTERVENTION_REQUIRED;
        this.processingNotes = notes;
    }

    /**
     * Check if message is eligible for retry
     */
    public boolean isEligibleForRetry() {
        return (status == DlqStatus.RETRY_SCHEDULED || status == DlqStatus.PENDING)
                && retryCount < maxRetries
                && (retryAfter == null || retryAfter.isBefore(LocalDateTime.now()));
    }

    public enum DlqStatus {
        PENDING,                        // Newly failed, not yet processed
        RETRY_SCHEDULED,                // Scheduled for automatic retry
        RETRYING,                       // Currently being retried
        RECOVERED,                      // Successfully recovered
        MANUAL_INTERVENTION_REQUIRED,   // Needs human review
        MAX_RETRIES_EXCEEDED,          // All automatic retries exhausted
        PERMANENT_FAILURE,              // Cannot be recovered
        ARCHIVED                        // Moved to archive after resolution
    }
}
