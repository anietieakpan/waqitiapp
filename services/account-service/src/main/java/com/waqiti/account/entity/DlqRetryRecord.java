package com.waqiti.account.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a message in the DLQ retry queue
 *
 * <p>Tracks Kafka messages scheduled for retry with exponential backoff.
 * Messages are processed by a scheduled job that checks next_retry_at timestamps.</p>
 *
 * <h3>Lifecycle:</h3>
 * <pre>
 * PENDING → RETRYING → SUCCESS (deleted after retention)
 *                    → FAILED (moved to permanent failures)
 *                    → CANCELLED (manual intervention)
 * </pre>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Entity
@Table(name = "dlq_retry_queue", indexes = {
    @Index(name = "idx_dlq_retry_next_retry", columnList = "next_retry_at"),
    @Index(name = "idx_dlq_retry_topic", columnList = "original_topic, status"),
    @Index(name = "idx_dlq_retry_created", columnList = "created_at"),
    @Index(name = "idx_dlq_retry_status", columnList = "status"),
    @Index(name = "idx_dlq_retry_correlation", columnList = "correlation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"payload", "exceptionStackTrace"})
public class DlqRetryRecord {

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

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Retry Strategy
    @Column(name = "retry_attempt", nullable = false)
    @Builder.Default
    private Integer retryAttempt = 0;

    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private Integer maxRetryAttempts = 3;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "retry_reason", length = 500)
    private String retryReason;

    @Column(name = "backoff_delay_ms", nullable = false)
    private Long backoffDelayMs;

    // Status Tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private RetryStatus status = RetryStatus.PENDING;

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

    @Column(name = "recovery_action", length = 100)
    private String recoveryAction;

    // Correlation for Tracing
    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    /**
     * Retry status enumeration
     */
    public enum RetryStatus {
        PENDING,      // Waiting for retry time
        RETRYING,     // Currently being retried
        SUCCESS,      // Retry succeeded
        FAILED,       // Retry failed (max attempts exceeded)
        CANCELLED     // Manually cancelled
    }

    /**
     * Pre-persist callback
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
            status = RetryStatus.PENDING;
        }
        if (retryAttempt == null) {
            retryAttempt = 0;
        }
        if (maxRetryAttempts == null) {
            maxRetryAttempts = 3;
        }
        if (createdBy == null) {
            createdBy = "system";
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
     * Check if retry should be attempted
     */
    public boolean shouldRetry() {
        return status == RetryStatus.PENDING &&
               nextRetryAt != null &&
               !nextRetryAt.isAfter(LocalDateTime.now()) &&
               retryAttempt < maxRetryAttempts;
    }

    /**
     * Increment retry attempt and update next retry time
     */
    public void incrementRetryAttempt(long nextBackoffMs) {
        this.retryAttempt++;
        this.backoffDelayMs = nextBackoffMs;
        this.nextRetryAt = LocalDateTime.now().plusNanos(nextBackoffMs * 1_000_000);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark retry as successful
     */
    public void markSuccess() {
        this.status = RetryStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark retry as failed
     */
    public void markFailed(String reason) {
        this.status = RetryStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancel retry
     */
    public void cancel(String reason) {
        this.status = RetryStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
}
