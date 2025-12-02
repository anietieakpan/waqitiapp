package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Configuration for automatic bill payment
 * Allows users to set up recurring automatic payments for bills
 */
@Entity
@Table(name = "auto_pay_configs", indexes = {
        @Index(name = "idx_autopay_user_id", columnList = "user_id"),
        @Index(name = "idx_autopay_bill_id", columnList = "bill_id"),
        @Index(name = "idx_autopay_biller_id", columnList = "biller_id"),
        @Index(name = "idx_autopay_status", columnList = "status"),
        @Index(name = "idx_autopay_next_payment", columnList = "next_payment_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_bill_autopay", columnNames = {"user_id", "bill_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoPayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "biller_id", nullable = false)
    private UUID billerId;

    @Column(name = "biller_connection_id", nullable = false)
    private UUID billerConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_amount_type", nullable = false, length = 30)
    private AutoPayAmountType paymentAmountType;

    @Column(name = "fixed_amount", precision = 19, scale = 4)
    private BigDecimal fixedAmount;

    @Column(name = "days_before_due_date")
    private Integer daysBeforeDueDate = 3;

    @Column(name = "minimum_amount_threshold", precision = 19, scale = 4)
    private BigDecimal minimumAmountThreshold;

    @Column(name = "maximum_amount_threshold", precision = 19, scale = 4)
    private BigDecimal maximumAmountThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AutoPayStatus status;

    @Column(name = "last_payment_id")
    private UUID lastPaymentId;

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "last_payment_amount", precision = 19, scale = 4)
    private BigDecimal lastPaymentAmount;

    @Column(name = "last_payment_status", length = 30)
    private String lastPaymentStatus;

    @Column(name = "next_payment_date")
    private LocalDateTime nextPaymentDate;

    @Column(name = "failure_count")
    private Integer failureCount = 0;

    @Column(name = "last_failure_reason", length = 500)
    private String lastFailureReason;

    @Column(name = "notify_before_payment", nullable = false)
    private Boolean notifyBeforePayment = true;

    @Column(name = "notification_hours_before")
    private Integer notificationHoursBefore = 24;

    @Column(name = "suspend_on_failure", nullable = false)
    private Boolean suspendOnFailure = true;

    @Column(name = "max_failure_count")
    private Integer maxFailureCount = 3;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    /**
     * Version field for optimistic locking
     * Prevents concurrent auto-pay configuration updates
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Business logic methods

    public boolean isActive() {
        return status == AutoPayStatus.ACTIVE;
    }

    public boolean shouldSuspendOnFailure() {
        return suspendOnFailure && failureCount >= maxFailureCount;
    }

    public boolean isAmountWithinThresholds(BigDecimal amount) {
        if (minimumAmountThreshold != null && amount.compareTo(minimumAmountThreshold) < 0) {
            return false;
        }
        if (maximumAmountThreshold != null && amount.compareTo(maximumAmountThreshold) > 0) {
            return false;
        }
        return true;
    }

    public BigDecimal calculatePaymentAmount(BigDecimal billAmount) {
        return switch (paymentAmountType) {
            case FULL_AMOUNT -> billAmount;
            case MINIMUM_DUE -> minimumAmountThreshold != null ? minimumAmountThreshold : billAmount;
            case FIXED_AMOUNT -> fixedAmount != null ? fixedAmount : billAmount;
            default -> billAmount;
        };
    }

    public void recordSuccess(UUID paymentId, BigDecimal amount) {
        this.lastPaymentId = paymentId;
        this.lastPaymentDate = LocalDateTime.now();
        this.lastPaymentAmount = amount;
        this.lastPaymentStatus = "SUCCESS";
        this.failureCount = 0;
        this.lastFailureReason = null;
    }

    public void recordFailure(String reason) {
        this.lastPaymentDate = LocalDateTime.now();
        this.lastPaymentStatus = "FAILED";
        this.lastFailureReason = reason;
        this.failureCount++;

        if (shouldSuspendOnFailure()) {
            this.status = AutoPayStatus.SUSPENDED;
        }
    }

    public void softDelete(String deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.status = AutoPayStatus.CANCELLED;
    }

    @PrePersist
    protected void onCreate() {
        if (daysBeforeDueDate == null) {
            daysBeforeDueDate = 3;
        }
        if (failureCount == null) {
            failureCount = 0;
        }
        if (notifyBeforePayment == null) {
            notifyBeforePayment = true;
        }
        if (notificationHoursBefore == null) {
            notificationHoursBefore = 24;
        }
        if (suspendOnFailure == null) {
            suspendOnFailure = true;
        }
        if (maxFailureCount == null) {
            maxFailureCount = 3;
        }
    }
}
