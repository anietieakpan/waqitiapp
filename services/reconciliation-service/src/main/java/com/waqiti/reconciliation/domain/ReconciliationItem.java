package com.waqiti.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ReconciliationItem - Core domain entity for reconciliation tracking
 *
 * Represents a single item that needs to be reconciled between systems.
 * This could be a transaction, ledger entry, bank statement line, or payment provider record.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Entity
@Table(name = "reconciliation_items", indexes = {
    @Index(name = "idx_reconciliation_item_status", columnList = "status"),
    @Index(name = "idx_reconciliation_item_type", columnList = "item_type"),
    @Index(name = "idx_reconciliation_item_source", columnList = "source_system"),
    @Index(name = "idx_reconciliation_item_date", columnList = "transaction_date"),
    @Index(name = "idx_reconciliation_item_amount", columnList = "amount"),
    @Index(name = "idx_reconciliation_item_external_id", columnList = "external_reference_id"),
    @Index(name = "idx_reconciliation_batch", columnList = "reconciliation_batch_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReconciliationItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "reconciliation_batch_id", nullable = false)
    private String reconciliationBatchId;

    @Column(name = "item_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReconciliationItemType itemType;

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(name = "external_reference_id", length = 255)
    private String externalReferenceId;

    @Column(name = "internal_reference_id", length = 255)
    private String internalReferenceId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "counterparty", length = 255)
    private String counterparty;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReconciliationStatus status;

    @Column(name = "matched_item_id")
    private String matchedItemId;

    @Column(name = "match_confidence_score", precision = 5, scale = 4)
    private BigDecimal matchConfidenceScore;

    @Column(name = "match_method", length = 50)
    private String matchMethod;

    @Column(name = "discrepancy_id")
    private String discrepancyId;

    @Column(name = "reconciled_by")
    private String reconciledBy;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = ReconciliationStatus.PENDING;
        }
    }

    public enum ReconciliationItemType {
        TRANSACTION,
        LEDGER_ENTRY,
        BANK_STATEMENT_LINE,
        PAYMENT_PROVIDER_RECORD,
        SETTLEMENT_RECORD,
        FEE_RECORD,
        REFUND_RECORD,
        CHARGEBACK_RECORD
    }

    public enum ReconciliationStatus {
        PENDING,
        MATCHED,
        PARTIALLY_MATCHED,
        UNMATCHED,
        DISCREPANCY_DETECTED,
        UNDER_REVIEW,
        RESOLVED,
        EXCLUDED,
        CANCELLED
    }

    public boolean isPending() {
        return ReconciliationStatus.PENDING.equals(status);
    }

    public boolean isMatched() {
        return ReconciliationStatus.MATCHED.equals(status);
    }

    public boolean hasDiscrepancy() {
        return ReconciliationStatus.DISCREPANCY_DETECTED.equals(status);
    }

    public void markAsMatched(String matchedItemId, BigDecimal confidence, String method) {
        this.status = ReconciliationStatus.MATCHED;
        this.matchedItemId = matchedItemId;
        this.matchConfidenceScore = confidence;
        this.matchMethod = method;
        this.reconciledAt = LocalDateTime.now();
    }

    public void markAsDiscrepancy(String discrepancyId) {
        this.status = ReconciliationStatus.DISCREPANCY_DETECTED;
        this.discrepancyId = discrepancyId;
    }

    public void markAsResolved(String reconciledBy) {
        this.status = ReconciliationStatus.RESOLVED;
        this.reconciledBy = reconciledBy;
        this.reconciledAt = LocalDateTime.now();
    }
}
