package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID requestorId;

    @Column(nullable = false)
    private UUID recipientId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRequestStatus status;

    @Column
    private String referenceNumber;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(nullable = false, name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;
    
    @Setter
    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Creates a new payment request
     */
    public static PaymentRequest create(UUID requestorId, UUID recipientId, BigDecimal amount, 
                                      String currency, String description, int expiryHours) {
        PaymentRequest request = new PaymentRequest();
        request.requestorId = requestorId;
        request.recipientId = recipientId;
        request.amount = amount;
        request.currency = currency;
        request.description = description;
        request.status = PaymentRequestStatus.PENDING;
        request.referenceNumber = generateReferenceNumber();
        request.expiryDate = LocalDateTime.now().plusHours(expiryHours);
        request.createdAt = LocalDateTime.now();
        request.updatedAt = LocalDateTime.now();
        return request;
    }

    /**
     * Approves the payment request
     */
    public void approve(UUID transactionId) {
        validatePending();
        validateNotExpired();
        
        this.status = PaymentRequestStatus.APPROVED;
        this.transactionId = transactionId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Rejects the payment request
     */
    public void reject() {
        validatePending();
        validateNotExpired();
        
        this.status = PaymentRequestStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancels the payment request
     */
    public void cancel() {
        validatePending();
        
        this.status = PaymentRequestStatus.CANCELED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the payment request as expired
     */
    public void expire() {
        if (this.status != PaymentRequestStatus.PENDING) {
            throw new IllegalStateException("Cannot expire a payment request that is not pending");
        }
        
        if (!isExpired()) {
            throw new IllegalStateException("Cannot expire a payment request that is not yet expired");
        }
        
        this.status = PaymentRequestStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if the payment request is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    /**
     * Gets the status as a String
     */
    public String getStatus() {
        return this.status != null ? this.status.name() : null;
    }

    /**
     * Sets the external transaction ID (e.g., ACH-uuid, STRIPE-txn-id)
     */
    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    /**
     * Validates that the payment request is pending
     */
    private void validatePending() {
        if (this.status != PaymentRequestStatus.PENDING) {
            throw new IllegalStateException("Payment request is not in PENDING status");
        }
    }

    /**
     * Validates that the payment request is not expired
     */
    private void validateNotExpired() {
        if (isExpired()) {
            throw new IllegalStateException("Payment request is expired");
        }
    }

    /**
     * Generates a unique reference number
     */
    private static String generateReferenceNumber() {
        return "PR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}