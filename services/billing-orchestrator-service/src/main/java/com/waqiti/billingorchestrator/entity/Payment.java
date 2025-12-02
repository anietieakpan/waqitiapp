package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Entity
 * Tracks payments made against billing cycles
 */
@Entity
@Table(name = "billing_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Payment extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    // Amounts
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "processing_fee", precision = 19, scale = 4)
    private BigDecimal processingFee;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    // Timestamps
    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // Processing details
    @Column(name = "processor")
    private String processor;

    @Column(name = "processor_reference")
    private String processorReference;

    @Column(name = "processor_response_code")
    private String processorResponseCode;

    @Column(name = "processor_response_message", columnDefinition = "TEXT")
    private String processorResponseMessage;

    // Retry information
    @Column(name = "attempt_number")
    private Integer attemptNumber = 1;

    @Column(name = "max_attempts")
    private Integer maxAttempts = 3;

    @Column(name = "retry_after")
    private LocalDateTime retryAfter;

    @Column(name = "auto_retry")
    private Boolean autoRetry = true;

    // Refund information
    @Column(name = "refund_amount", precision = 19, scale = 4)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refund_reference")
    private String refundReference;

    @Column(name = "is_partial_refund")
    private Boolean isPartialRefund = false;

    // Customer details
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    // Metadata
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Payment types
     */
    public enum PaymentType {
        AUTOMATIC,             // Auto-charge on due date
        MANUAL,                // Customer-initiated payment
        RETRY,                 // Retry after failure
        PARTIAL,               // Partial payment
        OVERPAYMENT,          // Payment exceeding due amount
        ADVANCE,               // Advance payment
        DEPOSIT,               // Security deposit
        REFUND                 // Refund payment
    }

    /**
     * Payment methods
     */
    public enum PaymentMethod {
        CREDIT_CARD,
        DEBIT_CARD,
        BANK_TRANSFER,
        DIRECT_DEBIT,
        WALLET,
        MOBILE_MONEY,
        CASH,
        CHECK,
        CRYPTO,
        OTHER
    }

    /**
     * Payment status
     */
    public enum PaymentStatus {
        PENDING,               // Payment initiated
        PROCESSING,            // Being processed
        AUTHORIZED,            // Authorized but not captured
        CAPTURED,              // Successfully captured
        SETTLED,               // Funds settled
        FAILED,                // Payment failed
        CANCELLED,             // Cancelled before processing
        REFUNDED,              // Fully refunded
        PARTIALLY_REFUNDED,    // Partially refunded
        DISPUTED,              // Under dispute
        EXPIRED                // Authorization expired
    }

    // Business methods
    
    public void calculateNetAmount() {
        if (amount != null && processingFee != null) {
            netAmount = amount.subtract(processingFee);
        } else if (amount != null) {
            netAmount = amount;
        }
    }
    
    public boolean isSuccessful() {
        return status == PaymentStatus.CAPTURED || 
               status == PaymentStatus.SETTLED;
    }
    
    public boolean canRetry() {
        return status == PaymentStatus.FAILED && 
               autoRetry && 
               attemptNumber < maxAttempts &&
               (retryAfter == null || LocalDateTime.now().isAfter(retryAfter));
    }
    
    public boolean canRefund() {
        return (status == PaymentStatus.CAPTURED || status == PaymentStatus.SETTLED) &&
               (refundAmount == null || refundAmount.compareTo(amount) < 0);
    }
    
    public BigDecimal getRemainingRefundableAmount() {
        if (!canRefund()) {
            return BigDecimal.ZERO;
        }
        
        if (refundAmount == null) {
            return amount;
        }
        
        return amount.subtract(refundAmount);
    }
}