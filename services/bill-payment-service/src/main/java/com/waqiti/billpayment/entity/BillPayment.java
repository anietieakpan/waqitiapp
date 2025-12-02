package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a payment transaction for a bill
 * Tracks the complete lifecycle of a bill payment from initiation to completion
 */
@Entity
@Table(name = "bill_payments", indexes = {
        @Index(name = "idx_payment_user_id", columnList = "user_id"),
        @Index(name = "idx_payment_bill_id", columnList = "bill_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_scheduled_date", columnList = "scheduled_date"),
        @Index(name = "idx_payment_user_status", columnList = "user_id, status"),
        @Index(name = "idx_payment_external_id", columnList = "external_payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillPaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "wallet_transaction_id")
    private UUID walletTransactionId;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "biller_confirmation_number", length = 100)
    private String billerConfirmationNumber;

    @Column(name = "external_payment_id", length = 100)
    private String externalPaymentId;

    @Column(name = "cashback_amount", precision = 19, scale = 4)
    private BigDecimal cashbackAmount;

    @Column(name = "cashback_earned", nullable = false)
    private Boolean cashbackEarned = false;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "payment_note", columnDefinition = "TEXT")
    private String paymentNote;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Version field for optimistic locking
     * Prevents concurrent modification conflicts
     * JPA automatically increments this on each update
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Business logic methods

    public boolean isRetryable() {
        return status == BillPaymentStatus.FAILED && retryCount < maxRetries;
    }

    public boolean isProcessing() {
        return status == BillPaymentStatus.PROCESSING || status == BillPaymentStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == BillPaymentStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == BillPaymentStatus.FAILED || status == BillPaymentStatus.REJECTED;
    }

    public void markAsProcessing() {
        this.status = BillPaymentStatus.PROCESSING;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsCompleted(String billerConfirmation) {
        this.status = BillPaymentStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.billerConfirmationNumber = billerConfirmation;
    }

    public void markAsFailed(String reason) {
        this.status = BillPaymentStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.failureReason = reason;
        this.retryCount++;

        if (isRetryable()) {
            // Exponential backoff: 5, 15, 45 minutes
            long minutesToWait = (long) (5 * Math.pow(3, retryCount - 1));
            this.nextRetryAt = LocalDateTime.now().plusMinutes(minutesToWait);
        }
    }

    public void applyCashback(BigDecimal cashback) {
        this.cashbackAmount = cashback;
        this.cashbackEarned = true;
    }

    @PrePersist
    protected void onCreate() {
        if (currency == null) {
            currency = "USD";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        if (cashbackEarned == null) {
            cashbackEarned = false;
        }
        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID().toString();
        }
    }
}
