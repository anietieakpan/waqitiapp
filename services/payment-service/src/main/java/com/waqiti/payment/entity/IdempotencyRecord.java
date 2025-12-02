package com.waqiti.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency record entity for preventing duplicate payment processing
 *
 * DATABASE SCHEMA:
 * - Primary key: UUID (id)
 * - Unique constraint: idempotency_key (enforces exactly-once semantics)
 * - Indexes: status, created_at, expires_at (for efficient queries)
 * - TTL: 24 hours (configurable)
 *
 * LIFECYCLE:
 * 1. PROCESSING: Created when request starts processing
 * 2. COMPLETED: Updated when processing succeeds
 * 3. FAILED: Updated when processing fails (allows retry)
 *
 * AUDIT FIELDS:
 * - duplicate_request_count: Number of duplicate attempts
 * - last_duplicate_request_at: Timestamp of most recent duplicate
 * - processing_time_ms: Duration of processing for performance monitoring
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "payment_idempotency_records",
       indexes = {
           @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
           @Index(name = "idx_status", columnList = "status"),
           @Index(name = "idx_created_at", columnList = "created_at"),
           @Index(name = "idx_expires_at", columnList = "expires_at"),
           @Index(name = "idx_user_id", columnList = "user_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Unique idempotency key (typically transactionId or requestId)
     * MUST be unique across all requests
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /**
     * Type of request (PAYMENT, TRANSFER, REFUND, etc.)
     */
    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    /**
     * Current status of idempotency record
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    /**
     * Original request payload (JSON) for audit trail
     */
    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    /**
     * Cached response payload (JSON) for duplicate requests
     */
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * User ID who initiated the request
     */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * Timestamp when request processing started
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when request processing completed
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Expiration timestamp (for automatic cleanup)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Number of duplicate requests detected
     */
    @Column(name = "duplicate_request_count", nullable = false)
    @Builder.Default
    private Integer duplicateRequestCount = 0;

    /**
     * Timestamp of most recent duplicate request
     */
    @Column(name = "last_duplicate_request_at")
    private LocalDateTime lastDuplicateRequestAt;

    /**
     * Processing duration in milliseconds (for performance monitoring)
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * Error message if processing failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version")
    private Long version;
}
