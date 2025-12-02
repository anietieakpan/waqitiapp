package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Reversal Entity
 * 
 * Tracks all transaction reversals for audit trail and reconciliation.
 * Critical for maintaining balanced books and regulatory compliance.
 */
@Entity
@Table(name = "transaction_reversals",
    indexes = {
        @Index(name = "idx_original_transaction_id", columnList = "original_transaction_id"),
        @Index(name = "idx_reversal_transaction_id", columnList = "reversal_transaction_id"),
        @Index(name = "idx_reversal_date", columnList = "reversal_date"),
        @Index(name = "idx_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_original_transaction", columnNames = {"original_transaction_id"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionReversal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "original_transaction_id", nullable = false, unique = true)
    private String originalTransactionId;
    
    @Column(name = "reversal_transaction_id", nullable = false)
    private String reversalTransactionId;
    
    @Column(name = "debit_entry_id", nullable = false)
    private UUID debitEntryId;
    
    @Column(name = "credit_entry_id", nullable = false)
    private UUID creditEntryId;
    
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Column(name = "reversal_reason", nullable = false)
    private String reversalReason;
    
    @Column(name = "reversal_type", nullable = false)
    private String reversalType;
    
    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;
    
    @Column(name = "approved_by")
    private String approvedBy;
    
    @Column(name = "reversal_date", nullable = false)
    private LocalDateTime reversalDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReversalStatus status;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum ReversalStatus {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}