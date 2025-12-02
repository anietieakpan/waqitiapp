package com.waqiti.common.idempotency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Database-Level Idempotency Record
 *
 * CRITICAL: This entity provides the PRIMARY defense against duplicate event processing
 * in financial systems. The unique constraint on event_id ensures that no event can be
 * processed more than once, even in the face of:
 * - Kafka message retries
 * - Network failures
 * - Application crashes mid-processing
 * - Race conditions across multiple instances
 *
 * This is the STRIPE/SQUARE approach: Database as source of truth for idempotency.
 *
 * ARCHITECTURE:
 * - Layer 1: Database unique constraint (this entity)
 * - Layer 2: Distributed lock (prevents concurrent attempts)
 * - Layer 3: Redis cache (fast duplicate detection)
 *
 * USAGE:
 * 1. Before processing event: INSERT with status=PROCESSING
 * 2. If INSERT fails (duplicate key): Event already processed, skip
 * 3. Process event (business logic)
 * 4. UPDATE status=COMPLETED with result
 * 5. On error: UPDATE status=FAILED (allows retry)
 *
 * STATUS FLOW:
 * PROCESSING → COMPLETED (success)
 * PROCESSING → FAILED (error, allows retry)
 * FAILED → PROCESSING (retry attempt)
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-08
 */
@Entity
@Table(
    name = "processed_events",
    indexes = {
        @Index(name = "idx_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_processing_status", columnList = "processing_status"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_completed_at", columnList = "completed_at"),
        @Index(name = "idx_consumer_name_created", columnList = "consumer_name,created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /**
     * Unique event identifier from Kafka message
     * This is the PRIMARY idempotency key
     * Format: eventId from message payload, or generated from topic+partition+offset
     */
    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    /**
     * Business entity ID (e.g., paymentId, transactionId)
     * Used for correlation and debugging
     */
    @Column(name = "entity_id", length = 255)
    private String entityId;

    /**
     * Type of entity being processed (e.g., PAYMENT, TRANSACTION, BALANCE_UPDATE)
     */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /**
     * Consumer/service that processed this event
     */
    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    /**
     * Kafka topic from which event was consumed
     */
    @Column(name = "kafka_topic", length = 255)
    private String kafkaTopic;

    /**
     * Kafka partition
     */
    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    /**
     * Kafka offset
     */
    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    /**
     * Processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus status;

    /**
     * Serialized result of processing (for cache retrieval on duplicates)
     * Stored as JSON for flexibility
     */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    /**
     * Error message if processing failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Error stacktrace for debugging (stored for FAILED status only)
     */
    @Column(name = "error_stacktrace", columnDefinition = "TEXT")
    private String errorStacktrace;

    /**
     * Number of retry attempts
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * When processing started (for detecting stale locks)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    /**
     * When processing completed (successfully or failed)
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Processing duration in milliseconds
     */
    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    /**
     * Trace ID for distributed tracing correlation
     */
    @Column(name = "trace_id", length = 100)
    private String traceId;

    /**
     * Additional metadata (JSON format)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // Business logic helpers

    /**
     * Check if this event is currently being processed
     */
    public boolean isProcessing() {
        return status == ProcessingStatus.PROCESSING;
    }

    /**
     * Check if this event completed successfully
     */
    public boolean isCompleted() {
        return status == ProcessingStatus.COMPLETED;
    }

    /**
     * Check if this event failed processing
     */
    public boolean isFailed() {
        return status == ProcessingStatus.FAILED;
    }

    /**
     * Check if this processing attempt is stale (likely crashed/hung)
     * @param timeoutMinutes How long to wait before considering stale
     */
    public boolean isStale(int timeoutMinutes) {
        if (!isProcessing()) {
            return false;
        }
        return createdAt.isBefore(Instant.now().minusSeconds(timeoutMinutes * 60L));
    }

    /**
     * Mark as completed successfully
     */
    public void markCompleted(String result, long durationMs) {
        this.status = ProcessingStatus.COMPLETED;
        this.result = result;
        this.completedAt = Instant.now();
        this.processingDurationMs = durationMs;
        this.errorMessage = null;
        this.errorStacktrace = null;
    }

    /**
     * Mark as failed
     */
    public void markFailed(String errorMessage, String stacktrace, long durationMs) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.errorStacktrace = stacktrace;
        this.completedAt = Instant.now();
        this.processingDurationMs = durationMs;
        this.retryCount++;
    }

    /**
     * Reset for retry attempt
     */
    public void resetForRetry() {
        this.status = ProcessingStatus.PROCESSING;
        this.errorMessage = null;
        this.errorStacktrace = null;
        this.completedAt = null;
        this.processingDurationMs = null;
        this.retryCount++;
    }

    /**
     * Processing status enumeration
     */
    public enum ProcessingStatus {
        PROCESSING,     // Currently being processed
        COMPLETED,      // Successfully completed
        FAILED          // Failed (eligible for retry)
    }
}
