package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction entity representing payment transactions
 * CRITICAL FIX: Added @Version for optimistic locking to prevent double-spending
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_payment", columnList = "payment_id"),
    @Index(name = "idx_transaction_user", columnList = "user_id"),
    @Index(name = "idx_transaction_status", columnList = "status"),
    @Index(name = "idx_transaction_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    
    @Column(name = "provider_transaction_id")
    private String providerTransactionId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "provider", nullable = false)
    private String provider;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    public enum TransactionType {
        PAYMENT,
        REFUND,
        PAYOUT,
        TRANSFER,
        ADJUSTMENT
    }
    
    public enum Status {
        PENDING,
        PROCESSING,
        AUTHORIZED,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED,
        PARTIALLY_REFUNDED,
        REFUNDED
    }
}