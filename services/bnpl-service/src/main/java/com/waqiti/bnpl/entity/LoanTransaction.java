/**
 * Loan Transaction Entity
 * Tracks all transactions related to loan accounts
 */
package com.waqiti.bnpl.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates in concurrent transaction processing
     * Financial transactions must maintain ACID properties with optimistic locking
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;
    
    @Column(name = "transaction_reference", unique = true, nullable = false)
    private String transactionReference;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "principal_amount", precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", precision = 19, scale = 4)
    private BigDecimal interestAmount;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "penalty_amount", precision = 19, scale = 4)
    private BigDecimal penaltyAmount;
    
    @Column(nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "payment_channel")
    private String paymentChannel;
    
    @Column(name = "external_reference")
    private String externalReference;
    
    @Column(name = "bank_reference")
    private String bankReference;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "value_date")
    private LocalDateTime valueDate;
    
    @Column(name = "processed_date")
    private LocalDateTime processedDate;
    
    @Column(name = "processed_by")
    private UUID processedBy;
    
    @Column(name = "balance_before", precision = 15, scale = 2)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;
    
    @Column(name = "installment_id")
    private UUID installmentId;
    
    @Column(name = "reversal_reference")
    private String reversalReference;
    
    @Column(name = "is_reversed")
    private Boolean isReversed;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (isReversed == null) {
            isReversed = false;
        }
        if (principalAmount == null) {
            principalAmount = BigDecimal.ZERO;
        }
        if (interestAmount == null) {
            interestAmount = BigDecimal.ZERO;
        }
        if (feeAmount == null) {
            feeAmount = BigDecimal.ZERO;
        }
        if (penaltyAmount == null) {
            penaltyAmount = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean isDebit() {
        return transactionType == TransactionType.DISBURSEMENT ||
               transactionType == TransactionType.WRITE_OFF ||
               transactionType == TransactionType.ADJUSTMENT_DEBIT;
    }
    
    public boolean isCredit() {
        return transactionType == TransactionType.REPAYMENT ||
               transactionType == TransactionType.PREPAYMENT ||
               transactionType == TransactionType.ADJUSTMENT_CREDIT;
    }
    
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }
    
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }
    
    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }
    
    public boolean isReversible() {
        return isCompleted() && !isReversed && 
               (transactionType == TransactionType.REPAYMENT || 
                transactionType == TransactionType.PREPAYMENT ||
                transactionType == TransactionType.DISBURSEMENT);
    }
    
    public BigDecimal getTotalDeductionAmount() {
        return principalAmount.add(interestAmount).add(feeAmount).add(penaltyAmount);
    }
    
    public enum TransactionType {
        DISBURSEMENT,        // Loan disbursement
        REPAYMENT,          // Regular installment payment
        PREPAYMENT,         // Early/excess payment
        PENALTY,            // Penalty charges
        FEE,                // Fee charges
        INTEREST_ACCRUAL,   // Interest accrual
        WRITE_OFF,          // Write-off transaction
        REVERSAL,           // Transaction reversal
        ADJUSTMENT_CREDIT,  // Manual credit adjustment
        ADJUSTMENT_DEBIT,   // Manual debit adjustment
        PARTIAL_PREPAYMENT, // Partial prepayment
        FULL_PREPAYMENT,    // Full loan prepayment
        INTEREST_WAIVER,    // Interest waiver
        FEE_WAIVER         // Fee waiver
    }
    
    public enum TransactionStatus {
        PENDING,            // Transaction initiated
        PROCESSING,         // Being processed
        COMPLETED,          // Successfully completed
        FAILED,             // Transaction failed
        CANCELLED,          // Transaction cancelled
        REVERSED,           // Transaction reversed
        ON_HOLD            // Transaction on hold
    }
}