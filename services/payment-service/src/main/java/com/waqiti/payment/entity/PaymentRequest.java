package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentRequest Entity - Production-Grade JPA Entity
 *
 * Represents a payment request in the system with full audit trail,
 * optimistic locking, and JSONB metadata support.
 *
 * FEATURES:
 * - Optimistic locking with @Version
 * - Audit timestamps (created_at, updated_at)
 * - JSONB metadata for flexible data storage
 * - Proper indexes for query performance
 * - Immutable ID after creation
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Production-Ready)
 */
@Entity
@Table(
    name = "payment_requests",
    indexes = {
        @Index(name = "idx_payment_requests_user", columnList = "user_id, created_at"),
        @Index(name = "idx_payment_requests_status", columnList = "status, created_at"),
        @Index(name = "idx_payment_requests_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_payment_requests_expires", columnList = "expires_at"),
        @Index(name = "idx_payment_requests_recipient", columnList = "recipient_id, created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"metadata"})
@EqualsAndHashCode(of = {"id"})
public class PaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * User initiating the payment
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Recipient of the payment (nullable for certain payment types)
     */
    @Column(name = "recipient_id")
    private UUID recipientId;

    /**
     * Payment amount with 4 decimal places for precision
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    /**
     * Payment status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * Payment method used
     */
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    /**
     * Idempotency key for duplicate prevention
     */
    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    /**
     * Human-readable description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Flexible metadata stored as JSONB
     */
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * External reference ID (e.g., Stripe payment intent ID)
     */
    @Column(name = "external_reference_id", length = 255)
    private String externalReferenceId;

    /**
     * Failure reason if payment failed
     */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /**
     * Failure code for programmatic handling
     */
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    /**
     * Fraud check score (0.0 - 1.0)
     */
    @Column(name = "fraud_score", precision = 5, scale = 4)
    private BigDecimal fraudScore;

    /**
     * IP address of the request originator
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * Optimistic locking version
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Creation timestamp (immutable)
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp (automatically updated)
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Expiration timestamp for pending requests
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Completion timestamp
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Payment status enum
     */
    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        REQUIRES_ACTION,
        REQUIRES_CONFIRMATION,
        AUTHORIZED,
        SUCCEEDED,
        FAILED,
        CANCELED,
        EXPIRED,
        REFUNDED,
        PARTIALLY_REFUNDED
    }

    /**
     * Pre-persist callback to set defaults
     */
    @PrePersist
    protected void prePersist() {
        if (currency == null) {
            currency = "USD";
        }
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        if (expiresAt == null && status == PaymentStatus.PENDING) {
            // Default expiration: 72 hours
            expiresAt = Instant.now().plusSeconds(72 * 60 * 60);
        }
    }

    /**
     * Check if payment request is expired
     */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if payment is in terminal state
     */
    @Transient
    public boolean isTerminal() {
        return status == PaymentStatus.SUCCEEDED ||
               status == PaymentStatus.FAILED ||
               status == PaymentStatus.CANCELED ||
               status == PaymentStatus.EXPIRED;
    }

    /**
     * Check if payment can be refunded
     */
    @Transient
    public boolean isRefundable() {
        return status == PaymentStatus.SUCCEEDED ||
               status == PaymentStatus.PARTIALLY_REFUNDED;
    }
}
