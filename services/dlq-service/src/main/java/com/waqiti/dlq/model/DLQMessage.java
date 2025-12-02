package com.waqiti.dlq.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a message in the Dead Letter Queue.
 *
 * This stores all failed Kafka messages for analysis and recovery.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Entity
@Table(name = "dlq_messages", indexes = {
    @Index(name = "idx_dlq_topic", columnList = "original_topic"),
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_priority", columnList = "priority"),
    @Index(name = "idx_dlq_created_at", columnList = "created_at"),
    @Index(name = "idx_dlq_retry_count", columnList = "retry_count")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "message_key", length = 500)
    private String messageKey;

    @Column(name = "message_payload", nullable = false, columnDefinition = "TEXT")
    private String messagePayload;

    @Column(name = "message_headers", columnDefinition = "JSONB")
    private Map<String, String> messageHeaders;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(name = "error_class", length = 500)
    private String errorClass;

    @Column(name = "consumer_group", length = 255)
    private String consumerGroup;

    @Column(name = "failed_consumer_class", length = 500)
    private String failedConsumerClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DLQStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    private DLQPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_strategy", length = 50)
    private RecoveryStrategy recoveryStrategy;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "recovery_notes", columnDefinition = "TEXT")
    private String recoveryNotes;

    @Column(name = "assigned_to", length = 255)
    private String assignedTo;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * DLQ message status.
     */
    public enum DLQStatus {
        NEW,                    // Newly arrived in DLQ
        ANALYZING,              // Being analyzed
        READY_FOR_RETRY,        // Ready to retry
        RETRYING,               // Currently retrying
        MANUAL_REVIEW_REQUIRED, // Needs manual intervention
        RECOVERED,              // Successfully recovered
        SKIPPED,                // Intentionally skipped
        PERMANENT_FAILURE       // Cannot be recovered
    }

    /**
     * Priority level for DLQ processing.
     */
    public enum DLQPriority {
        CRITICAL,  // Financial transactions, compliance
        HIGH,      // Payment flows, user-facing features
        MEDIUM,    // Background processes, analytics
        LOW        // Non-critical, nice-to-have
    }

    /**
     * Recovery strategy for the message.
     */
    public enum RecoveryStrategy {
        AUTOMATIC_RETRY,        // Automatic retry with backoff
        COMPENSATING_TRANSACTION, // Execute compensating transaction
        MANUAL_INTERVENTION,    // Requires manual fix
        SKIP,                   // Skip this message
        REPROCESS_WITH_FIX      // Reprocess after code fix
    }
}
