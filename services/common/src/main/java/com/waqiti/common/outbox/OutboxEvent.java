package com.waqiti.common.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Production-ready Outbox Event Entity
 *
 * Implements the Transactional Outbox Pattern for reliable event publishing
 * in distributed systems. This entity ensures exactly-once delivery semantics
 * by storing events in the same database transaction as business operations.
 *
 * Key Features:
 * - Optimistic locking with @Version to prevent lost updates
 * - Composite indexes for efficient event polling
 * - Status enum for proper state management
 * - Audit fields for compliance and debugging
 * - Retry tracking for resilient event processing
 *
 * @author Waqiti Platform Team
 * @version 2.0 - Production Ready
 */
@Entity
@Table(name = "outbox_events", indexes = {
    // Composite index for polling query: "SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY createdAt LIMIT 100"
    @Index(name = "idx_outbox_status_created", columnList = "status,createdAt"),

    // Index for aggregate lookup: "SELECT * FROM outbox_events WHERE aggregateType = 'Payment' AND aggregateId = '123'"
    @Index(name = "idx_outbox_aggregate", columnList = "aggregateType,aggregateId"),

    // Index for event type filtering and analytics
    @Index(name = "idx_outbox_event_type", columnList = "eventType"),

    // Index for failed event queries
    @Index(name = "idx_outbox_status_retry", columnList = "status,retryCount,nextRetryAt"),

    // Index for cleanup queries (delete old processed events)
    @Index(name = "idx_outbox_processed_cleanup", columnList = "status,processedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking version field - CRITICAL FOR PRODUCTION
     * Prevents lost updates when multiple consumers try to process the same event
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Type of the aggregate that generated this event
     * Example: "Payment", "Wallet", "Transaction"
     */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /**
     * Unique identifier of the aggregate instance
     * Example: payment ID, wallet ID, transaction ID
     */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    /**
     * Type of the event
     * Example: "PaymentCompleted", "WalletCredited", "TransactionFailed"
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Event payload in JSON format
     * Contains the full event data
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * Event processing status
     * PENDING → PROCESSING → PROCESSED
     * PENDING → PROCESSING → FAILED (with retry)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    /**
     * Timestamp when the event was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * User/service that created this event
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Timestamp when the event was successfully processed
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Service/worker that processed this event
     */
    @Column(name = "processed_by", length = 100)
    private String processedBy;

    /**
     * Number of retry attempts (for failed processing)
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Timestamp for next retry attempt (exponential backoff)
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Error message from last failed processing attempt
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * Exception stack trace (for debugging)
     */
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    /**
     * Correlation ID for distributed tracing
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
        if (version == null) {
            version = 0L;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * Marks the event as successfully processed
     */
    public void markProcessed(String processedBy) {
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = processedBy;
    }

    /**
     * Marks the event as failed with error details
     */
    public void markFailed(String errorMessage, String errorDetails) {
        this.status = OutboxEventStatus.FAILED;
        this.errorMessage = errorMessage;
        this.errorDetails = errorDetails;
        this.retryCount++;

        // Exponential backoff: 2^retryCount minutes
        int delayMinutes = (int) Math.pow(2, Math.min(retryCount, 10));
        this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }

    /**
     * Marks the event as processing (prevents duplicate processing)
     */
    public void markProcessing(String processedBy) {
        this.status = OutboxEventStatus.PROCESSING;
        this.processedBy = processedBy;
    }
}
