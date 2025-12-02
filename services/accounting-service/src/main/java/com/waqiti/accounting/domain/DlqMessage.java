package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
 * Dead Letter Queue Message Entity
 * Represents a failed Kafka message that requires retry or manual intervention
 */
@Entity
@Table(name = "dlq_message", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_next_retry", columnList = "next_retry_at"),
    @Index(name = "idx_dlq_topic", columnList = "topic,status"),
    @Index(name = "idx_dlq_consumer_group", columnList = "consumer_group,status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Size(max = 255)
    @Column(name = "message_id", unique = true, nullable = false)
    private String messageId;

    @NotNull
    @Size(max = 255)
    @Column(name = "topic", nullable = false)
    private String topic;

    @Size(max = 255)
    @Column(name = "partition_key")
    private String partitionKey;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> messagePayload;

    @NotNull
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Size(max = 500)
    @Column(name = "error_class")
    private String errorClass;

    @Min(0)
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Min(1)
    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private Integer maxRetryAttempts = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DlqStatus status = DlqStatus.PENDING;

    @NotNull
    @Size(max = 255)
    @Column(name = "consumer_group", nullable = false)
    private String consumerGroup;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @NotNull
    @Column(name = "original_timestamp", nullable = false)
    private LocalDateTime originalTimestamp;

    @NotNull
    @Column(name = "first_failure_at", nullable = false, updatable = false)
    private LocalDateTime firstFailureAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Size(max = 100)
    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (firstFailureAt == null) {
            firstFailureAt = now;
        }
        if (originalTimestamp == null) {
            originalTimestamp = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate next retry time using exponential backoff
     * Base delay: 1 minute, max delay: 24 hours
     */
    public void calculateNextRetry() {
        if (retryCount >= maxRetryAttempts) {
            status = DlqStatus.MANUAL_REVIEW;
            nextRetryAt = null;
        } else {
            // Exponential backoff: 2^retryCount minutes, capped at 24 hours
            long delayMinutes = Math.min((long) Math.pow(2, retryCount), 1440);
            nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    /**
     * Increment retry count and update next retry time
     */
    public void incrementRetry() {
        retryCount++;
        lastRetryAt = LocalDateTime.now();
        calculateNextRetry();
    }

    /**
     * Mark as resolved
     */
    public void markResolved(String resolvedBy, String notes) {
        this.status = DlqStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = notes;
    }

    /**
     * Check if retry should be attempted
     */
    public boolean shouldRetry() {
        return status == DlqStatus.PENDING
            && retryCount < maxRetryAttempts
            && (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }
}
