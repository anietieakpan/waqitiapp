package com.waqiti.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Journal Entry entity for recording individual debit/credit entries
 */
@Entity
@Table(name = "journal_entries", indexes = {
    @Index(name = "idx_journal_transaction", columnList = "transactionId"),
    @Index(name = "idx_journal_account", columnList = "accountId"),
    @Index(name = "idx_journal_date", columnList = "entryDate"),
    @Index(name = "idx_journal_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accountId", nullable = false)
    private LedgerAccount account;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDateTime entryDate;

    @Column(nullable = false)
    private Integer sequenceNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryStatus status;

    @Column
    private UUID reversalEntryId;

    @Column
    private Boolean isReversal;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum EntryType {
        DEBIT,
        CREDIT
    }

    public enum EntryStatus {
        PENDING,
        POSTED,
        REVERSED,
        CANCELLED,
        FAILED
    }
}