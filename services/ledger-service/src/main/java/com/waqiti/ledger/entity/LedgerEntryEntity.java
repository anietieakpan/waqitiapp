package com.waqiti.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ledger Entry Entity
 * 
 * Represents individual ledger entries in the double-entry bookkeeping system.
 * Each transaction creates at least two ledger entries (debit and credit).
 */
@Entity
@Table(name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_account", columnList = "account_id"),
        @Index(name = "idx_ledger_transaction", columnList = "transaction_id"),
        @Index(name = "idx_ledger_date", columnList = "transaction_date"),
        @Index(name = "idx_ledger_period", columnList = "accounting_period"),
        @Index(name = "idx_ledger_status", columnList = "status"),
        @Index(name = "idx_ledger_type", columnList = "entry_type"),
        @Index(name = "idx_ledger_reference", columnList = "reference_number")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"account", "transaction"})
@ToString(exclude = {"account", "transaction"})
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Column(name = "entry_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "base_amount", precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "accounting_period", length = 20)
    private String accountingPeriod;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EntryStatus status = EntryStatus.POSTED;

    @Column(name = "posting_date")
    private LocalDateTime postingDate;

    @Column(name = "reversal_of_entry_id")
    private UUID reversalOfEntryId;

    @Column(name = "reversed_by_entry_id")
    private UUID reversedByEntryId;

    @Column(name = "is_closing_entry")
    private Boolean isClosingEntry = false;

    @Column(name = "is_adjusting_entry")
    private Boolean isAdjustingEntry = false;

    @Column(name = "is_reversing_entry")
    private Boolean isReversingEntry = false;

    // Reconciliation fields
    @Column(name = "reconciled")
    private Boolean reconciled = false;

    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "reconciled_date")
    private LocalDateTime reconciledDate;

    @Column(name = "reconciled_by")
    private String reconciledBy;

    // Running balance (denormalized for performance)
    @Column(name = "running_balance", precision = 19, scale = 4)
    private BigDecimal runningBalance;

    // Document attachments
    @Column(name = "attachment_urls", length = 1000)
    private String attachmentUrls;

    // Audit fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Additional metadata
    @Column(name = "cost_center_id")
    private UUID costCenterId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "tags", length = 500)
    private String tags;

    public enum EntryType {
        DEBIT,
        CREDIT,
        RESERVATION,
        RELEASE,
        PENDING
    }

    public enum EntryStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        POSTED,
        REVERSED,
        CANCELLED
    }
}