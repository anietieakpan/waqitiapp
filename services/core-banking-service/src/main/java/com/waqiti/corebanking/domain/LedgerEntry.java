package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ledger Entry Entity - Double Entry Bookkeeping
 *
 * Represents individual entries in the double-entry bookkeeping system.
 * Every financial transaction creates balanced debit and credit entries.
 *
 * EntityGraph Optimization:
 * - withTransaction: Eagerly loads associated transaction
 * - withAccount: Eagerly loads associated account
 * - withTransactionAndAccount: Loads both transaction and account
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_account_id", columnList = "accountId"),
    @Index(name = "idx_entry_date", columnList = "entryDate"),
    @Index(name = "idx_entry_type", columnList = "entryType"),
    @Index(name = "idx_reference", columnList = "reference"),
    @Index(name = "idx_account_date", columnList = "accountId, entryDate")
})
@NamedEntityGraphs({
    @NamedEntityGraph(
        name = "LedgerEntry.withTransaction",
        attributeNodes = @NamedAttributeNode("transaction")
    ),
    @NamedEntityGraph(
        name = "LedgerEntry.withAccount",
        attributeNodes = @NamedAttributeNode("account")
    ),
    @NamedEntityGraph(
        name = "LedgerEntry.withTransactionAndAccount",
        attributeNodes = {
            @NamedAttributeNode("transaction"),
            @NamedAttributeNode("account")
        }
    )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    private Transaction transaction;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;

    @Column(name = "entry_number", nullable = false)
    private Long entryNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "running_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal runningBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "reference", length = 50)
    private String reference;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EntryStatus status;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @Column(name = "value_date", nullable = false)
    private LocalDateTime valueDate;

    @Column(name = "posting_date")
    private LocalDateTime postingDate;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "original_entry_id")
    private UUID originalEntryId;

    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public enum EntryType {
        DEBIT("Debit Entry"),
        CREDIT("Credit Entry");

        private final String description;

        EntryType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum EntryStatus {
        PENDING("Entry pending posting"),
        POSTED("Entry successfully posted"),
        REVERSED("Entry has been reversed"),
        FAILED("Entry posting failed"),
        CANCELLED("Entry was cancelled");

        private final String description;

        EntryStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Business Logic Methods

    public boolean isDebit() {
        return entryType == EntryType.DEBIT;
    }

    public boolean isCredit() {
        return entryType == EntryType.CREDIT;
    }

    public boolean isPosted() {
        return status == EntryStatus.POSTED;
    }

    public boolean isReversed() {
        return status == EntryStatus.REVERSED;
    }

    public boolean canBeReversed() {
        return isPosted() && reversalEntryId == null;
    }

    public BigDecimal getSignedAmount() {
        return isDebit() ? amount.negate() : amount;
    }

    public void markAsPosted() {
        this.status = EntryStatus.POSTED;
        this.postingDate = LocalDateTime.now();
    }

    public void markAsReversed(UUID reversalEntryId) {
        this.status = EntryStatus.REVERSED;
        this.reversalEntryId = reversalEntryId;
    }

    public void markAsFailed() {
        this.status = EntryStatus.FAILED;
    }
}