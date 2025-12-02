package com.waqiti.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_ledger_account_id", columnList = "accountId"),
    @Index(name = "idx_ledger_entry_date", columnList = "entryDate"),
    @Index(name = "idx_ledger_entry_type", columnList = "entryType"),
    @Index(name = "idx_ledger_reference_id", columnList = "referenceId")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String accountId;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "entry_date", nullable = false)
    private Instant entryDate;

    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "running_balance", precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "is_reversal")
    private Boolean isReversal = false;

    @Column(name = "original_entry_id")
    private UUID originalEntryId;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    public enum EntryType {
        // Basic double-entry types
        DEBIT,
        CREDIT,
        
        // Advanced balance management types
        RESERVATION,            // Reserve funds (reduce available balance)
        RELEASE,               // Release reserved funds
        PENDING,               // Pending transactions
        AUTHORIZATION_HOLD,    // Authorization holds (credit cards, etc.)
        AUTHORIZATION_RELEASE  // Release authorization holds
    }

    @PrePersist
    protected void onCreate() {
        if (entryDate == null) {
            entryDate = Instant.now();
        }
    }

    public boolean isDebit() {
        return EntryType.DEBIT.equals(entryType);
    }

    public boolean isCredit() {
        return EntryType.CREDIT.equals(entryType);
    }

    public Instant getTransactionDate() {
        return entryDate;
    }

    // Add relationship to JournalEntry
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    private JournalEntry journalEntry;
}