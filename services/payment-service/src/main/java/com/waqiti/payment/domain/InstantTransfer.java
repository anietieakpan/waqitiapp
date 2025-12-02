package com.waqiti.payment.domain;

import com.waqiti.common.domain.Money;
import com.waqiti.payment.commons.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain entity representing an instant transfer (Zelle-style, FedNow, RTP)
 * Provides real-time money transfers with immediate settlement
 */
@Entity
@Table(name = "instant_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantTransfer {

    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Version for optimistic locking to prevent race conditions during status transitions
     * Critical for instant transfers with multiple concurrent state changes
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 19, scale = 2)),
        @AttributeOverride(name = "currency.currencyCode", column = @Column(name = "currency", length = 3))
    })
    private Money amount;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_method", nullable = false)
    private TransferMethod transferMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "fraud_score")
    private BigDecimal fraudScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "network_transaction_id")
    private String networkTransactionId;

    @Column(name = "network_response", columnDefinition = "TEXT")
    private String networkResponse;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "processing_time")
    private Long processingTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Transfer methods supported for instant transfers
     */
    public enum TransferMethod {
        FEDNOW("FedNow", "Federal Reserve FedNow Service"),
        RTP("RTP", "Real-Time Payments Network"),
        ZELLE("Zelle", "Zelle Network"),
        INTERNAL("Internal", "Internal Waqiti Transfer");

        private final String displayName;
        private final String description;

        TransferMethod(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Checks if the transfer can be canceled based on current status
     */
    public boolean canBeCanceled() {
        return status == PaymentStatus.CREATED || 
               status == PaymentStatus.PENDING || 
               status == PaymentStatus.FRAUD_CHECKING ||
               status == PaymentStatus.RESERVING_FUNDS ||
               status == PaymentStatus.FUNDS_RESERVED;
    }

    /**
     * Checks if the transfer is in a final state
     */
    public boolean isFinal() {
        return status.isFinal();
    }

    /**
     * Sets the transfer status with validation
     */
    public void setStatus(PaymentStatus newStatus) {
        if (this.status != null && !this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid status transition from %s to %s for transfer %s", 
                    this.status, newStatus, this.id));
        }
        this.status = newStatus;
    }

    /**
     * Marks the transfer as canceled
     */
    public void cancel(String reason) {
        if (!canBeCanceled()) {
            throw new IllegalStateException(
                String.format("Transfer %s cannot be canceled in status %s", this.id, this.status));
        }
        this.status = PaymentStatus.CANCELLED;
        this.cancellationReason = reason;
        this.canceledAt = LocalDateTime.now();
    }

    /**
     * Marks the transfer as completed
     */
    public void complete() {
        this.status = PaymentStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the transfer as failed with reason
     */
    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the transfer as blocked due to fraud
     */
    public void block(String reason) {
        this.status = PaymentStatus.BLOCKED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }
}