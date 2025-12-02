package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transaction record for payments made through payment links
 */
@Entity
@Table(name = "payment_link_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_link_id", nullable = false)
    private PaymentLink paymentLink;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;
    
    @Column(name = "payer_id")
    private UUID payerId; // null for anonymous payments
    
    @Column(name = "payer_email", length = 100)
    private String payerEmail;
    
    @Column(name = "payer_name", length = 100)
    private String payerName;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "payment_note", length = 500)
    private String paymentNote;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "payment_reference", length = 100)
    private String paymentReference; // External payment processor reference
    
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;
    
    @Column(name = "failure_reason", length = 200)
    private String failureReason;
    
    @ElementCollection
    @CollectionTable(name = "payment_link_transaction_metadata", 
                     joinColumns = @JoinColumn(name = "transaction_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    // Business logic methods
    
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }
    
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }
    
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markPending() {
        this.status = TransactionStatus.PENDING;
    }
    
    public void markRefunded() {
        this.status = TransactionStatus.REFUNDED;
    }
    
    public void setPaymentProvider(String provider, String reference, String externalId) {
        this.paymentReference = reference;
        this.providerTransactionId = externalId;
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put("provider", provider);
    }
    
    public boolean isAnonymous() {
        return payerId == null;
    }
    
    public enum TransactionStatus {
        PENDING,    // Payment initiated but not completed
        COMPLETED,  // Payment successfully processed
        FAILED,     // Payment failed
        CANCELLED,  // Payment cancelled by payer
        REFUNDED    // Payment refunded
    }
}