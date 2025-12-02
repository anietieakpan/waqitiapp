package com.waqiti.payment.entity;

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
 * Escrow Transaction Entity
 * Represents individual deposit, release, or refund transactions within an escrow account
 * 
 * COMPLIANCE: Immutable audit trail for all escrow movements
 * SECURITY: All transactions are versioned and cannot be deleted
 */
@Entity
@Table(name = "escrow_transactions", indexes = {
    @Index(name = "idx_escrow_txn_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_escrow_txn_account", columnList = "escrow_id"),
    @Index(name = "idx_escrow_txn_type", columnList = "transaction_type"),
    @Index(name = "idx_escrow_txn_status", columnList = "status"),
    @Index(name = "idx_escrow_txn_initiator", columnList = "initiated_by"),
    @Index(name = "idx_escrow_txn_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;
    
    @Column(name = "escrow_id", nullable = false, length = 100)
    private String escrowId;
    
    @Column(name = "contract_id", nullable = false, length = 100)
    private String contractId;
    
    @Column(name = "transaction_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "from_party_id", length = 100)
    private String fromPartyId;
    
    @Column(name = "to_party_id", length = 100)
    private String toPartyId;
    
    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;
    
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "payment_reference", length = 200)
    private String paymentReference;
    
    @Column(name = "external_transaction_id", length = 200)
    private String externalTransactionId;
    
    @Column(name = "authorization_code", length = 100)
    private String authorizationCode;
    
    @Column(name = "approval_count", nullable = false)
    @Builder.Default
    private Integer approvalCount = 0;
    
    @Column(name = "required_approvals", nullable = false)
    private Integer requiredApprovals;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "failure_code", length = 50)
    private String failureCode;
    
    @Column(name = "release_conditions", columnDefinition = "jsonb")
    private String releaseConditions;
    
    @Column(name = "conditions_met", nullable = false)
    @Builder.Default
    private Boolean conditionsMet = false;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @Column(name = "fee_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;
    
    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;
    
    @Column(name = "is_reversible", nullable = false)
    @Builder.Default
    private Boolean isReversible = false;
    
    @Column(name = "reversed", nullable = false)
    @Builder.Default
    private Boolean reversed = false;
    
    @Column(name = "reversal_transaction_id", length = 100)
    private String reversalTransactionId;
    
    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;
    
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;
    
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
    
    public enum TransactionType {
        DEPOSIT,              // Buyer deposits funds into escrow
        RELEASE,              // Funds released to seller
        PARTIAL_RELEASE,      // Milestone payment release
        REFUND,               // Funds refunded to buyer
        PARTIAL_REFUND,       // Partial refund to buyer
        FEE_DEDUCTION,        // Escrow service fee
        DISPUTE_HOLD,         // Funds held during dispute
        DISPUTE_RELEASE,      // Funds released after dispute
        ADJUSTMENT,           // Balance adjustment
        REVERSAL              // Transaction reversal
    }
    
    public enum TransactionStatus {
        PENDING,              // Transaction initiated
        PENDING_APPROVAL,     // Awaiting required approvals
        AUTHORIZED,           // Authorized but not processed
        PROCESSING,           // Being processed
        COMPLETED,            // Successfully completed
        FAILED,               // Failed to process
        CANCELLED,            // Cancelled by user
        REVERSED,             // Transaction reversed
        EXPIRED,              // Authorization expired
        DECLINED,             // Declined by payment processor
        ON_HOLD               // Held for review
    }
    
    /**
     * Check if transaction can be approved
     */
    public boolean canBeApproved() {
        return status == TransactionStatus.PENDING_APPROVAL && approvalCount < requiredApprovals;
    }
    
    /**
     * Add approval
     */
    public void addApproval() {
        this.approvalCount++;
        if (this.approvalCount >= this.requiredApprovals) {
            this.status = TransactionStatus.AUTHORIZED;
            this.authorizedAt = LocalDateTime.now();
        }
    }
    
    /**
     * Mark as completed
     */
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.netAmount = this.amount.subtract(this.feeAmount);
    }
    
    /**
     * Mark as failed
     */
    public void markFailed(String reason, String code) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.failureCode = code;
        this.failedAt = LocalDateTime.now();
    }
    
    /**
     * Reverse transaction
     */
    public void reverse(String reversalTxnId) {
        if (!isReversible) {
            throw new IllegalStateException("Transaction is not reversible");
        }
        this.reversed = true;
        this.reversalTransactionId = reversalTxnId;
        this.reversedAt = LocalDateTime.now();
        this.status = TransactionStatus.REVERSED;
    }
}