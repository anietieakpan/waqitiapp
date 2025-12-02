package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bank Statement Entity
 * 
 * Represents bank statements and their individual entries for
 * reconciliation with internal ledger records.
 */
@Entity
@Table(name = "bank_statements", indexes = {
    @Index(name = "idx_bank_statement_account", columnList = "bankAccountId"),
    @Index(name = "idx_bank_statement_date", columnList = "statementDate"),
    @Index(name = "idx_bank_statement_period", columnList = "startDate, endDate"),
    @Index(name = "idx_bank_statement_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "statement_id")
    private UUID statementId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "statement_number", nullable = false, length = 50)
    private String statementNumber;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "opening_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal closingBalance;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatementStatus status = StatementStatus.PENDING;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_name", length = 255)
    private String accountName;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @Column(name = "imported_by", length = 100)
    private String importedBy;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "reconciled_by", length = 100)
    private String reconciledBy;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "total_debits", precision = 19, scale = 4)
    private BigDecimal totalDebits;

    @Column(name = "total_credits", precision = 19, scale = 4)
    private BigDecimal totalCredits;

    @Column(name = "transaction_count")
    private Integer transactionCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    // Relationships
    @OneToMany(mappedBy = "bankStatement", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BankStatementEntry> entries;

    /**
     * Statement Status
     */
    public enum StatementStatus {
        PENDING,        // Statement imported but not processed
        PROCESSING,     // Statement is being processed
        RECONCILED,     // Statement has been reconciled
        PARTIALLY_RECONCILED, // Some entries reconciled
        FAILED,         // Processing failed
        CANCELLED       // Statement was cancelled
    }

    /**
     * Business logic methods
     */
    
    public boolean isReconciled() {
        return status == StatementStatus.RECONCILED;
    }

    public boolean canBeReconciled() {
        return status == StatementStatus.PENDING || status == StatementStatus.PROCESSING ||
               status == StatementStatus.PARTIALLY_RECONCILED;
    }

    public boolean isBalanced() {
        BigDecimal calculatedClosing = openingBalance.add(totalCredits).subtract(totalDebits);
        return closingBalance.compareTo(calculatedClosing) == 0;
    }

    public void markAsReconciled(String reconciledBy) {
        this.status = StatementStatus.RECONCILED;
        this.reconciledAt = LocalDateTime.now();
        this.reconciledBy = reconciledBy;
    }

    public void calculateTotals() {
        if (entries != null && !entries.isEmpty()) {
            this.totalDebits = entries.stream()
                .filter(entry -> entry.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(entry -> entry.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            this.totalCredits = entries.stream()
                .filter(entry -> entry.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(BankStatementEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            this.transactionCount = entries.size();
        }
    }
}

/**
 * Bank Statement Entry Entity
 * 
 * Individual transactions within a bank statement.
 */
@Entity
@Table(name = "bank_statement_entries", indexes = {
    @Index(name = "idx_bank_entry_statement", columnList = "statementId"),
    @Index(name = "idx_bank_entry_date", columnList = "transactionDate"),
    @Index(name = "idx_bank_entry_reference", columnList = "referenceNumber"),
    @Index(name = "idx_bank_entry_status", columnList = "reconciliationStatus")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BankStatementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id", insertable = false, updatable = false)
    private BankStatement bankStatement;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "transaction_code", length = 20)
    private String transactionCode;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    @Column(name = "counterparty_name", length = 255)
    private String counterpartyName;

    @Column(name = "counterparty_account", length = 50)
    private String counterpartyAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status")
    @Builder.Default
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNMATCHED;

    @Column(name = "matched_ledger_entry_id")
    private UUID matchedLedgerEntryId;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "matched_by", length = 100)
    private String matchedBy;

    @Column(name = "matching_confidence", precision = 5, scale = 4)
    private BigDecimal matchingConfidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Transaction Types
     */
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT,
        FEE,
        INTEREST,
        DIVIDEND,
        CHECK,
        DIRECT_DEBIT,
        STANDING_ORDER,
        CARD_PAYMENT,
        ATM_WITHDRAWAL,
        BANK_CHARGE,
        REFUND,
        CORRECTION,
        OTHER
    }

    /**
     * Reconciliation Status
     */
    public enum ReconciliationStatus {
        UNMATCHED,      // Not yet matched with ledger
        MATCHED,        // Matched with ledger entry
        PARTIALLY_MATCHED, // Partially matched
        DISPUTED,       // Match is disputed
        IGNORED,        // Entry ignored for reconciliation
        MANUAL_MATCH    // Manually matched
    }

    public boolean isMatched() {
        return reconciliationStatus == ReconciliationStatus.MATCHED ||
               reconciliationStatus == ReconciliationStatus.MANUAL_MATCH;
    }

    public boolean isDebit() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isCredit() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public void markAsMatched(UUID ledgerEntryId, String matchedBy, BigDecimal confidence) {
        this.reconciliationStatus = ReconciliationStatus.MATCHED;
        this.matchedLedgerEntryId = ledgerEntryId;
        this.matchedAt = LocalDateTime.now();
        this.matchedBy = matchedBy;
        this.matchingConfidence = confidence;
    }
}