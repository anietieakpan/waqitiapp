package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Journal Entry Entity
 * 
 * Represents a complete journal entry containing multiple ledger entries
 * that must balance according to double-entry accounting principles.
 */
@Entity
@Table(name = "journal_entries", indexes = {
    @Index(name = "idx_journal_entry_date", columnList = "entryDate"),
    @Index(name = "idx_journal_status", columnList = "status"),
    @Index(name = "idx_journal_type", columnList = "entryType"),
    @Index(name = "idx_journal_reference", columnList = "referenceNumber"),
    @Index(name = "idx_journal_period", columnList = "accountingPeriodId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "entry_number", nullable = false, unique = true, length = 50)
    private String entryNumber;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JournalStatus status = JournalStatus.DRAFT;

    @Column(name = "total_debits", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalDebits = BigDecimal.ZERO;

    @Column(name = "total_credits", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "accounting_period_id")
    private UUID accountingPeriodId;

    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "source_document_id", length = 100)
    private String sourceDocumentId;

    @Column(name = "source_document_type", length = 50)
    private String sourceDocumentType;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "posted_by", length = 100)
    private String postedBy;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversed_by", length = 100)
    private String reversedBy;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "original_journal_entry_id")
    private UUID originalJournalEntryId;

    @Column(name = "approval_required")
    @Builder.Default
    private Boolean approvalRequired = false;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approval_notes", length = 1000)
    private String approvalNotes;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // Relationships
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LedgerEntry> ledgerEntries;

    /**
     * Journal Entry Types
     */
    public enum EntryType {
        // Standard Entries
        STANDARD,           // Regular business transactions
        ADJUSTING,          // Period-end adjusting entries
        CLOSING,            // Period-end closing entries
        OPENING,            // Opening balance entries
        
        // Special Entries
        REVERSAL,           // Reversal of previous entries
        CORRECTION,         // Error corrections
        ACCRUAL,            // Accrual entries
        DEFERRAL,           // Deferral entries
        DEPRECIATION,       // Depreciation entries
        REVALUATION,        // Asset/liability revaluation
        
        // System Entries
        AUTOMATIC,          // System-generated entries
        RECURRING,          // Recurring journal entries
        ALLOCATION,         // Cost/revenue allocation
        CONSOLIDATION,      // Consolidation adjustments
        ELIMINATION,        // Intercompany eliminations
        
        // Reconciliation
        BANK_RECONCILIATION, // Bank reconciliation adjustments
        SUSPENSE_CLEARING,   // Suspense account clearing
        
        // Regulatory
        TAX_ADJUSTMENT,     // Tax-related adjustments
        AUDIT_ADJUSTMENT,   // Audit adjustments
        COMPLIANCE         // Regulatory compliance entries
    }

    /**
     * Journal Entry Status
     */
    public enum JournalStatus {
        DRAFT,              // Entry is being prepared
        PENDING_APPROVAL,   // Waiting for approval
        APPROVED,           // Approved but not posted
        POSTED,             // Posted to ledger
        REVERSED,           // Entry has been reversed
        CANCELLED,          // Entry was cancelled
        REJECTED,           // Entry was rejected
        ERROR,              // Entry has errors
        LOCKED              // Entry is locked (period closed)
    }

    /**
     * Business logic methods
     */
    
    public boolean isBalanced() {
        return totalDebits.compareTo(totalCredits) == 0;
    }

    public boolean canBePosted() {
        return status == JournalStatus.APPROVED && isBalanced() && 
               (ledgerEntries != null && !ledgerEntries.isEmpty());
    }

    public boolean canBeReversed() {
        return status == JournalStatus.POSTED && reversedAt == null;
    }

    public boolean requiresApproval() {
        return approvalRequired && approvedAt == null;
    }

    public boolean isReversalEntry() {
        return entryType == EntryType.REVERSAL || originalJournalEntryId != null;
    }

    public boolean isPeriodEndEntry() {
        return entryType == EntryType.CLOSING || entryType == EntryType.ADJUSTING;
    }

    public boolean isSystemGenerated() {
        return entryType == EntryType.AUTOMATIC || entryType == EntryType.RECURRING ||
               entryType == EntryType.ALLOCATION;
    }

    public void markAsPosted(String postedBy) {
        this.status = JournalStatus.POSTED;
        this.postedAt = LocalDateTime.now();
        this.postedBy = postedBy;
    }

    public void markAsReversed(String reversedBy, String reason) {
        this.status = JournalStatus.REVERSED;
        this.reversedAt = LocalDateTime.now();
        this.reversedBy = reversedBy;
        this.reversalReason = reason;
    }

    public void approve(String approvedBy, String notes) {
        this.status = JournalStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
        this.approvalNotes = notes;
    }

    public void reject(String rejectedBy, String reason) {
        this.status = JournalStatus.REJECTED;
        this.approvalNotes = reason;
        this.updatedBy = rejectedBy;
    }

    public void calculateTotals() {
        if (ledgerEntries != null && !ledgerEntries.isEmpty()) {
            this.totalDebits = ledgerEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            this.totalCredits = ledgerEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}