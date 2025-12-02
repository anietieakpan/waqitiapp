package com.waqiti.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Transaction Entity
 * 
 * Represents a complete accounting transaction that contains multiple ledger entries.
 * Ensures double-entry bookkeeping integrity.
 */
@Entity
@Table(name = "transactions",
    indexes = {
        @Index(name = "idx_transaction_number", columnList = "transaction_number", unique = true),
        @Index(name = "idx_transaction_date", columnList = "transaction_date"),
        @Index(name = "idx_transaction_type", columnList = "transaction_type"),
        @Index(name = "idx_transaction_status", columnList = "status"),
        @Index(name = "idx_transaction_period", columnList = "accounting_period"),
        @Index(name = "idx_transaction_company", columnList = "company_id"),
        @Index(name = "idx_ledger_company_period", columnList = "company_id, accounting_period"),
        @Index(name = "idx_ledger_status_approval", columnList = "status, approval_level"),
        @Index(name = "idx_ledger_company_fiscal", columnList = "company_id, fiscal_year"),
        @Index(name = "idx_ledger_source_status", columnList = "source_system, status"),
        @Index(name = "idx_ledger_company_status_date", columnList = "company_id, status, transaction_date")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"ledgerEntries"})
@ToString(exclude = {"ledgerEntries"})
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "transaction_number", nullable = false, unique = true, length = 50)
    private String transactionNumber;

    @Column(name = "transaction_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "total_debit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebitAmount;

    @Column(name = "total_credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCreditAmount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status = TransactionStatus.DRAFT;

    @Column(name = "accounting_period", length = 20)
    private String accountingPeriod;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "is_balanced")
    private Boolean isBalanced = false;

    @Column(name = "is_closing_transaction")
    private Boolean isClosingTransaction = false;

    @Column(name = "is_adjusting_transaction")
    private Boolean isAdjustingTransaction = false;

    @Column(name = "is_reversing_transaction")
    private Boolean isReversingTransaction = false;

    // Reversal information
    @Column(name = "reversal_of_transaction_id")
    private UUID reversalOfTransactionId;

    @Column(name = "reversed_by_transaction_id")
    private UUID reversedByTransactionId;

    @Column(name = "reversal_date")
    private LocalDateTime reversalDate;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    // Company and organizational data
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "cost_center_id")
    private UUID costCenterId;

    @Column(name = "project_id")
    private UUID projectId;

    // Source system information
    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "source_document_type", length = 50)
    private String sourceDocumentType;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    // Attachments and notes
    @Column(name = "attachment_urls", length = 1000)
    private String attachmentUrls;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "tags", length = 500)
    private String tags;

    // Approval workflow
    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;

    @Column(name = "approval_level")
    private Integer approvalLevel;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Posting information
    @Column(name = "posted_by", length = 100)
    private String postedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    // Audit fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    // Relationships
    // FINANCIAL SAFETY: Use limited cascade for ledger entries to prevent accidental deletion
    // Ledger entries are permanent financial records and should not be cascade deleted
    @OneToMany(mappedBy = "transaction", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    @OrderBy("entryType ASC, amount DESC")
    private Set<LedgerEntryEntity> ledgerEntries = new HashSet<>();

    // Business methods
    public void addLedgerEntry(LedgerEntryEntity entry) {
        ledgerEntries.add(entry);
        entry.setTransaction(this);
        recalculateTotals();
    }

    public void removeLedgerEntry(LedgerEntryEntity entry) {
        ledgerEntries.remove(entry);
        entry.setTransaction(null);
        recalculateTotals();
    }

    public void recalculateTotals() {
        totalDebitAmount = ledgerEntries.stream()
            .filter(e -> e.getEntryType() == LedgerEntryEntity.EntryType.DEBIT)
            .map(LedgerEntryEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        totalCreditAmount = ledgerEntries.stream()
            .filter(e -> e.getEntryType() == LedgerEntryEntity.EntryType.CREDIT)
            .map(LedgerEntryEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        isBalanced = totalDebitAmount.compareTo(totalCreditAmount) == 0;
    }

    public boolean canBePosted() {
        return isBalanced && 
               status == TransactionStatus.APPROVED && 
               (!requiresApproval || approvedAt != null);
    }

    public enum TransactionType {
        JOURNAL_ENTRY,
        INVOICE,
        PAYMENT,
        RECEIPT,
        TRANSFER,
        ADJUSTMENT,
        OPENING_BALANCE,
        CLOSING_ENTRY,
        REVERSAL,
        ACCRUAL,
        PREPAYMENT,
        DEPRECIATION,
        REVALUATION,
        CONSOLIDATION
    }

    public enum TransactionStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        POSTED,
        REVERSED,
        CANCELLED,
        ON_HOLD
    }
}