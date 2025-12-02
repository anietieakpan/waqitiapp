package com.waqiti.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ledger Transaction entity representing a complete double-entry transaction
 */
@Entity
@Table(name = "ledger_transactions", indexes = {
    @Index(name = "idx_ledger_trans_saga", columnList = "sagaId"),
    @Index(name = "idx_ledger_trans_date", columnList = "transactionDate"),
    @Index(name = "idx_ledger_trans_status", columnList = "status"),
    @Index(name = "idx_ledger_trans_type", columnList = "transactionType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String transactionNumber;

    @Column(length = 100)
    private String sagaId;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebits;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredits;

    @OneToMany(mappedBy = "transactionId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<JournalEntry> journalEntries;

    @Column
    private UUID initiatedBy;

    @Column
    private UUID approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private UUID reversedBy;

    @Column
    private LocalDateTime reversedAt;

    @Column
    private UUID reversalTransactionId;

    @Column(length = 100)
    private String externalReference;

    @Column(length = 64)
    private String checksum;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(columnDefinition = "TEXT")
    private String auditNotes;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum TransactionType {
        PAYMENT,
        TRANSFER,
        DEPOSIT,
        WITHDRAWAL,
        FEE,
        REFUND,
        ADJUSTMENT,
        INTEREST,
        DIVIDEND,
        PURCHASE,
        SALE,
        PAYROLL,
        TAX
    }

    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED,
        CANCELLED,
        SUSPENDED
    }

    /**
     * Validate that debits equal credits
     */
    public boolean isBalanced() {
        return totalDebits != null && totalCredits != null && 
               totalDebits.compareTo(totalCredits) == 0;
    }
}