package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    private String id;

    @Column(nullable = false)
    private UUID walletId;
    
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = true)
    private UUID sourceWalletId;

    @Column(nullable = true)
    private UUID targetWalletId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;
    
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    
    @Column(length = 100)
    private String paymentMethodId;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal balanceBefore;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String reference;
    
    @Column(length = 100)
    private String externalId;
    
    @Column(length = 100)
    private String providerTransactionId;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "transaction_metadata")
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, Object> metadata;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // Audit fields
    private String createdBy;

    private String updatedBy;
    
    // Compliance and blocking fields
    @Column(length = 500)
    private String blockReason;
    
    private LocalDateTime blockedAt;
    
    @Column(length = 100)
    private String blockedBy;
    
    @Column(columnDefinition = "boolean default false")
    private boolean monitoringEnabled;
    
    @Column(length = 50)
    private String monitoringLevel;
    
    @Column(columnDefinition = "boolean default false")
    private boolean autoReviewEnabled;
    
    private LocalDateTime autoReviewScheduled;
    
    @Column(columnDefinition = "boolean default false")
    private boolean autoReviewCompleted;
    
    @Column(length = 50)
    private String autoReviewResult;
    
    // Delay fields
    @Column(length = 500)
    private String delayReason;
    
    private LocalDateTime delayedAt;
    
    private LocalDateTime scheduledExecutionTime;
    
    @Column(columnDefinition = "boolean default false")
    private boolean delayExecuted;

    /**
     * Creates a new transaction
     */
    public static Transaction create(UUID sourceWalletId, UUID targetWalletId, BigDecimal amount,
            Currency currency, TransactionType type, String description) {
        Transaction transaction = new Transaction();
        transaction.id = UUID.randomUUID().toString();
        transaction.sourceWalletId = sourceWalletId;
        transaction.targetWalletId = targetWalletId;
        transaction.amount = amount;
        transaction.currency = currency;
        transaction.type = type;
        transaction.description = description;
        transaction.status = TransactionStatus.PENDING;
        transaction.createdAt = LocalDateTime.now();
        transaction.updatedAt = LocalDateTime.now();
        return transaction;
    }

    /**
     * Creates a new deposit transaction
     */
    public static Transaction createDeposit(UUID targetWalletId, BigDecimal amount,
            Currency currency, String description) {
        return create(null, targetWalletId, amount, currency, TransactionType.DEPOSIT, description);
    }

    /**
     * Creates a new withdrawal transaction
     */
    public static Transaction createWithdrawal(UUID sourceWalletId, BigDecimal amount,
            Currency currency, String description) {
        return create(sourceWalletId, null, amount, currency, TransactionType.WITHDRAWAL, description);
    }

    /**
     * Creates a new transfer transaction
     */
    public static Transaction createTransfer(UUID sourceWalletId, UUID targetWalletId,
            BigDecimal amount, Currency currency, String description) {
        return create(sourceWalletId, targetWalletId, amount, currency, TransactionType.TRANSFER, description);
    }

    /**
     * Marks the transaction as completed
     */
    public void complete(String externalId) {
        this.externalId = externalId;
        this.status = TransactionStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the transaction as failed
     */
    public void fail(String reason) {
        this.status = TransactionStatus.FAILED;
        this.description = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the transaction as in progress
     */
    public void markInProgress() {
        this.status = TransactionStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }
}