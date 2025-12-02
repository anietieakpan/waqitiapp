package com.waqiti.bnpl.domain;

import com.waqiti.bnpl.domain.enums.TransactionStatus;
import com.waqiti.bnpl.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a transaction in BNPL system
 */
@Entity
@Table(name = "bnpl_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BnplTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Version for optimistic locking to prevent race conditions during transaction processing
     * Critical for BNPL transactions with refund calculations and status updates
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bnpl_plan_id", nullable = false)
    private BnplPlan bnplPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id")
    private BnplInstallment installment;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "description")
    private String description;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "refunded_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "metadata")
    @Convert(converter = MetadataConverter.class)
    private MetadataMap metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Business methods

    public void markAsProcessed(String paymentReference) {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Can only process pending transactions");
        }
        this.status = TransactionStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
        this.paymentReference = paymentReference;
    }

    public void markAsFailed(String reason) {
        if (this.status == TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot fail a completed transaction");
        }
        this.status = TransactionStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.failureReason = reason;
        this.retryCount++;
    }

    public void scheduleRetry(LocalDateTime retryTime) {
        if (this.status != TransactionStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed transactions");
        }
        this.status = TransactionStatus.PENDING;
        this.nextRetryAt = retryTime;
    }

    public void refund(BigDecimal refundAmount) {
        if (this.status != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Can only refund completed transactions");
        }
        if (refundAmount.compareTo(this.amount.subtract(this.refundedAmount)) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds available amount");
        }
        this.refundedAmount = this.refundedAmount.add(refundAmount);
        if (this.refundedAmount.compareTo(this.amount) >= 0) {
            this.status = TransactionStatus.REFUNDED;
        }
    }

    public boolean isFullyRefunded() {
        return this.refundedAmount.compareTo(this.amount) >= 0;
    }

    public BigDecimal getRefundableAmount() {
        return this.amount.subtract(this.refundedAmount);
    }

    public boolean canRetry() {
        return this.status == TransactionStatus.FAILED && this.retryCount < 3;
    }
}