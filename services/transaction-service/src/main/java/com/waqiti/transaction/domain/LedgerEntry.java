package com.waqiti.transaction.domain;

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
import java.util.Map;
import java.util.UUID;

/**
 * Ledger Entry - Double-Entry Bookkeeping Record
 *
 * <p>Represents a single entry in the general ledger following GAAP principles.
 * Every financial transaction creates at least two ledger entries:
 * <ul>
 *   <li>One DEBIT entry (asset decrease or liability increase)</li>
 *   <li>One CREDIT entry (asset increase or liability decrease)</li>
 * </ul>
 *
 * <p>The sum of all debits must equal the sum of all credits in a batch.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_ledger_account_id", columnList = "account_id"),
        @Index(name = "idx_ledger_batch_id", columnList = "batch_id"),
        @Index(name = "idx_ledger_created_at", columnList = "created_at"),
        @Index(name = "idx_ledger_account_created", columnList = "account_id,created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "posted", nullable = false)
    @Builder.Default
    private Boolean posted = true;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
