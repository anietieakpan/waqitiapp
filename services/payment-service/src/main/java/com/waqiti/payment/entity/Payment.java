package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment entity - PRODUCTION READY
 *
 * CRITICAL ENHANCEMENTS:
 * - Added @Version for optimistic locking (prevents lost updates)
 * - Added audit fields (createdBy, updatedBy) for compliance
 * - Changed decimal scale from 2 to 4 for consistency
 * - Changed ID generation from IDENTITY to UUID for distributed systems
 * - Added EntityListeners for automatic audit population
 * - Added indexes for performance
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_user_id", columnList = "user_id"),
    @Index(name = "idx_payment_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_created_at", columnList = "created_at"),
    @Index(name = "idx_payment_payment_id", columnList = "payment_id", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, unique = true, updatable = false)
    private UUID paymentId;

    /**
     * CRITICAL FIX: Optimistic locking to prevent lost updates
     * Without this, concurrent payment updates could overwrite each other
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod; // ACH, CARD, WIRE, etc.

    /**
     * CRITICAL FIX: Changed scale from 2 to 4 for consistency with other services
     * scale=4 supports more precise currency calculations (e.g., crypto, forex)
     */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "USD";

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Audit fields for compliance (PCI DSS, SOC 2, GDPR)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "external_transaction_id", length = 255)
    private String externalTransactionId;

    /**
     * Idempotency support
     */
    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    /**
     * Additional tracking fields
     */
    @Column(name = "provider", length = 50)
    private String provider; // Stripe, PayPal, etc.

    @Column(name = "provider_payment_id", length = 255)
    private String providerPaymentId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    /**
     * Fraud detection fields
     */
    @Column(name = "fraud_score", precision = 5, scale = 2)
    private BigDecimal fraudScore;

    @Column(name = "fraud_checked_at")
    private LocalDateTime fraudCheckedAt;

    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @PrePersist
    protected void onCreate() {
        if (paymentId == null) {
            paymentId = UUID.randomUUID();
        }
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // Track completion time
        if (status == PaymentStatus.COMPLETED && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
        // Track failure time
        if ((status == PaymentStatus.FAILED || status == PaymentStatus.DECLINED) && failedAt == null) {
            failedAt = LocalDateTime.now();
        }
    }

    /**
     * Business logic methods
     */
    public boolean isCompleted() {
        return PaymentStatus.COMPLETED.equals(status);
    }

    public boolean isFailed() {
        return PaymentStatus.FAILED.equals(status) || PaymentStatus.DECLINED.equals(status);
    }

    public boolean isPending() {
        return PaymentStatus.PENDING.equals(status) || PaymentStatus.PROCESSING.equals(status);
    }

    public boolean canRetry() {
        return isFailed() && (retryCount == null || retryCount < 3);
    }

    public void incrementRetryCount() {
        this.retryCount = (retryCount == null ? 0 : retryCount) + 1;
        this.lastRetryAt = LocalDateTime.now();
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
