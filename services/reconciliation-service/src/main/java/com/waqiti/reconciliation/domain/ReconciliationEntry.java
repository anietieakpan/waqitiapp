package com.waqiti.reconciliation.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_entries", indexes = {
    @Index(name = "idx_reconciliation_date", columnList = "reconciliationDate"),
    @Index(name = "idx_account_number", columnList = "accountNumber"),
    @Index(name = "idx_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_entry_type", columnList = "entryType"),
    @Index(name = "idx_matched_entry_id", columnList = "matchedEntryId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReconciliationEntry extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "reconciliation_date", nullable = false)
    private LocalDateTime reconciliationDate;
    
    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;
    
    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    
    @Column(name = "external_reference", length = 100)
    private String externalReference;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Column(name = "balance_before", precision = 19, scale = 4)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ReconciliationSource source;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EntryStatus status;
    
    @Column(name = "value_date")
    private LocalDateTime valueDate;
    
    @Column(name = "processing_date")
    private LocalDateTime processingDate;
    
    @Column(name = "matched_entry_id")
    private UUID matchedEntryId;
    
    @Column(name = "matched_at")
    private LocalDateTime matchedAt;
    
    @Column(name = "matched_by", length = 100)
    private String matchedBy;
    
    @Column(name = "matching_score")
    private BigDecimal matchingScore;
    
    @Column(name = "variance_amount", precision = 19, scale = 4)
    private BigDecimal varianceAmount;
    
    @Column(name = "variance_reason", columnDefinition = "TEXT")
    private String varianceReason;
    
    @Column(name = "is_exception", nullable = false)
    private Boolean isException = false;
    
    @Column(name = "exception_reason", columnDefinition = "TEXT")
    private String exceptionReason;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;
    
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    
    @Column(name = "batch_id")
    private UUID batchId;
    
    @Column(name = "sequence_number")
    private Long sequenceNumber;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_entry_metadata", 
                     joinColumns = @JoinColumn(name = "entry_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", columnDefinition = "TEXT")
    private Map<String, String> metadata;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_entry_audit_trail", 
                     joinColumns = @JoinColumn(name = "entry_id"))
    @MapKeyColumn(name = "audit_key")
    @Column(name = "audit_value", columnDefinition = "TEXT")
    private Map<String, String> auditTrail;
    
    public enum EntryType {
        DEBIT,
        CREDIT,
        OPENING_BALANCE,
        CLOSING_BALANCE,
        FEE,
        INTEREST,
        TRANSFER_IN,
        TRANSFER_OUT,
        REVERSAL,
        ADJUSTMENT,
        FOREX_GAIN,
        FOREX_LOSS
    }
    
    public enum ReconciliationSource {
        INTERNAL_LEDGER,
        BANK_STATEMENT,
        PAYMENT_GATEWAY,
        CARD_PROCESSOR,
        EXTERNAL_BANK,
        NOSTRO_ACCOUNT,
        VOSTRO_ACCOUNT,
        CENTRAL_BANK,
        SWIFT_NETWORK,
        CLEARING_HOUSE,
        MANUAL_ENTRY
    }
    
    public enum EntryStatus {
        PENDING,
        MATCHED,
        UNMATCHED,
        PARTIALLY_MATCHED,
        EXCEPTION,
        INVESTIGATING,
        RESOLVED,
        CANCELLED,
        REVERSED,
        DISPUTED,
        CONFIRMED
    }
    
    public boolean isMatched() {
        return status == EntryStatus.MATCHED || status == EntryStatus.PARTIALLY_MATCHED;
    }
    
    public boolean hasVariance() {
        return varianceAmount != null && varianceAmount.compareTo(BigDecimal.ZERO) != 0;
    }
    
    public boolean requiresInvestigation() {
        return isException || status == EntryStatus.EXCEPTION || status == EntryStatus.INVESTIGATING;
    }
    
    public BigDecimal getAbsoluteAmount() {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }
}