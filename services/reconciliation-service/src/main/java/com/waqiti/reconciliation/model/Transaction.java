package com.waqiti.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing internal transactions for reconciliation
 */
@Entity
@Table(name = "reconciliation_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "merchant_id", length = 50)
    private String merchantId;

    @Column(name = "batch_id", length = 50)
    private String batchId;

    @Column(name = "reconciled", nullable = false)
    @Builder.Default
    private Boolean reconciled = false;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum TransactionType {
        DEBIT,
        CREDIT,
        TRANSFER,
        REFUND,
        CHARGEBACK,
        FEE,
        ADJUSTMENT
    }

    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        DISPUTED,
        REFUNDED
    }

    // Utility methods for reconciliation
    public boolean isReconcilable() {
        return status == TransactionStatus.COMPLETED && !reconciled;
    }

    public boolean requiresHighPriorityReconciliation() {
        return amount.compareTo(new BigDecimal("10000")) > 0 || 
               status == TransactionStatus.DISPUTED;
    }

    public String getUniqueIdentifier() {
        return id + "-" + amount + "-" + currency;
    }
}