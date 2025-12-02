package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Entity - Core Banking Transaction
 *
 * Represents a complete financial transaction that generates
 * balanced double-entry ledger entries.
 *
 * EntityGraph Optimization:
 * - withAccounts: Eagerly loads source and target accounts
 * - withAccountsAndParent: Loads accounts and parent transaction
 * - full: Loads all relationships (accounts, parent, initiator)
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_number", columnList = "transactionNumber", unique = true),
    @Index(name = "idx_source_account", columnList = "sourceAccountId"),
    @Index(name = "idx_target_account", columnList = "targetAccountId"),
    @Index(name = "idx_transaction_type", columnList = "transactionType"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_transaction_date", columnList = "transactionDate"),
    @Index(name = "idx_external_reference", columnList = "externalReference"),
    @Index(name = "idx_batch_id", columnList = "batchId")
})
@EntityListeners(AuditingEntityListener.class)
@NamedEntityGraphs({
    @NamedEntityGraph(
        name = "Transaction.withAccounts",
        attributeNodes = {
            @NamedAttributeNode("sourceAccount"),
            @NamedAttributeNode("targetAccount")
        }
    ),
    @NamedEntityGraph(
        name = "Transaction.withAccountsAndParent",
        attributeNodes = {
            @NamedAttributeNode("sourceAccount"),
            @NamedAttributeNode("targetAccount"),
            @NamedAttributeNode("parentTransaction")
        }
    ),
    @NamedEntityGraph(
        name = "Transaction.full",
        attributeNodes = {
            @NamedAttributeNode("sourceAccount"),
            @NamedAttributeNode("targetAccount"),
            @NamedAttributeNode("parentTransaction")
        }
    )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates in concurrent transactions
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    /**
     * Audit fields for compliance and tracking
     */
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "transaction_number", unique = true, nullable = false)
    private String transactionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", insertable = false, updatable = false)
    private Account sourceAccount;

    @Column(name = "target_account_id")
    private UUID targetAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id", insertable = false, updatable = false)
    private Account targetAccount;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "converted_amount", precision = 19, scale = 4)
    private BigDecimal convertedAmount;

    @Column(name = "converted_currency", length = 3)
    private String convertedCurrency;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "reference", length = 50)
    private String reference;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TransactionPriority priority;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "value_date", nullable = false)
    private LocalDateTime valueDate;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 3;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON for additional transaction data

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Column(name = "original_transaction_id")
    private UUID originalTransactionId;

    @Column(name = "parent_transaction_id")
    private UUID parentTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_transaction_id", insertable = false, updatable = false)
    private Transaction parentTransaction;

    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "compliance_check_id")
    private UUID complianceCheckId;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // REMOVED DUPLICATE FIELDS - createdBy, modifiedBy, and version are declared above (lines 50-63)
    // CRITICAL FIX: Duplicate @Version field was causing JPA initialization failure

    public enum TransactionType {
        // User-to-User Transactions
        P2P_TRANSFER("Peer-to-peer transfer"),
        P2P_REQUEST("Payment request"),
        
        // Account Management
        DEPOSIT("Deposit to account"),
        WITHDRAWAL("Withdrawal from account"),
        INTERNAL_TRANSFER("Internal account transfer"),
        
        // Fee Transactions
        FEE_CHARGE("Fee charge"),
        FEE_REFUND("Fee refund"),
        MAINTENANCE_FEE("Maintenance fee"),
        OVERDRAFT_FEE("Overdraft fee"),
        ATM_FEE("ATM usage fee"),
        WIRE_FEE("Wire transfer fee"),
        INTERNATIONAL_FEE("International transaction fee"),
        
        // Interest Transactions
        INTEREST_CREDIT("Interest credit"),
        INTEREST_DEBIT("Interest debit"),
        
        // System Transactions
        SYSTEM_ADJUSTMENT("System balance adjustment"),
        RECONCILIATION("Reconciliation entry"),
        REVERSAL("Transaction reversal"),
        
        // External Transactions
        BANK_TRANSFER("Bank transfer"),
        CARD_PAYMENT("Card payment"),
        ACH_TRANSFER("ACH transfer"),
        WIRE_TRANSFER("Wire transfer"),
        
        // Merchant Transactions
        MERCHANT_PAYMENT("Merchant payment"),
        MERCHANT_REFUND("Merchant refund"),
        
        // Compliance Transactions
        HOLD_PLACEMENT("Compliance hold"),
        HOLD_RELEASE("Compliance hold release");

        private final String description;

        TransactionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TransactionStatus {
        PENDING("Transaction pending authorization"),
        AUTHORIZED("Transaction authorized"),
        PROCESSING("Transaction being processed"),
        COMPLETED("Transaction completed successfully"),
        FAILED("Transaction failed"),
        CANCELLED("Transaction cancelled"),
        REVERSED("Transaction reversed"),
        PARTIALLY_COMPLETED("Transaction partially completed"),
        REQUIRES_APPROVAL("Transaction requires manual approval"),
        COMPLIANCE_HOLD("Transaction on compliance hold");

        private final String description;

        TransactionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TransactionPriority {
        LOW("Low priority"),
        NORMAL("Normal priority"),
        HIGH("High priority"),
        URGENT("Urgent priority"),
        IMMEDIATE("Immediate processing required");

        private final String description;

        TransactionPriority(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Business Logic Methods

    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }

    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    public boolean canBeReversed() {
        return isCompleted() && reversalTransactionId == null;
    }

    public boolean canRetry() {
        return isFailed() && retryCount < maxRetryAttempts;
    }

    public boolean isP2PTransaction() {
        return transactionType == TransactionType.P2P_TRANSFER || 
               transactionType == TransactionType.P2P_REQUEST;
    }

    public boolean isSystemTransaction() {
        return transactionType == TransactionType.SYSTEM_ADJUSTMENT ||
               transactionType == TransactionType.RECONCILIATION ||
               transactionType == TransactionType.REVERSAL;
    }

    public boolean requiresApproval() {
        return status == TransactionStatus.REQUIRES_APPROVAL;
    }

    public boolean isOnComplianceHold() {
        return status == TransactionStatus.COMPLIANCE_HOLD;
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = amount;
        if (feeAmount != null) {
            total = total.add(feeAmount);
        }
        return total;
    }

    public void markAsAuthorized(UUID authorizedBy) {
        this.status = TransactionStatus.AUTHORIZED;
        this.authorizedBy = authorizedBy;
        this.authorizedAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }

    public void markAsReversed(UUID reversalTransactionId) {
        this.status = TransactionStatus.REVERSED;
        this.reversalTransactionId = reversalTransactionId;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void setComplianceHold() {
        this.status = TransactionStatus.COMPLIANCE_HOLD;
    }

    public void releaseComplianceHold() {
        if (status == TransactionStatus.COMPLIANCE_HOLD) {
            this.status = TransactionStatus.PENDING;
        }
    }
}