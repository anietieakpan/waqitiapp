package com.waqiti.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Reconciliation Entity
 *
 * Represents a reconciliation process for verifying account balances
 * and transactions against external sources.
 */
@Entity
@Table(name = "account_reconciliations", indexes = {
    @Index(name = "idx_reconciliation_account_id", columnList = "account_id"),
    @Index(name = "idx_reconciliation_date", columnList = "reconciliation_date"),
    @Index(name = "idx_reconciliation_status", columnList = "status"),
    @Index(name = "idx_reconciliation_type", columnList = "reconciliation_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReconciliation {

    @Id
    @Column(name = "reconciliation_id", length = 36)
    private String reconciliationId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_type", nullable = false)
    private ReconciliationType reconciliationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReconciliationStatus status = ReconciliationStatus.PENDING;

    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    @Column(name = "expected_balance", precision = 19, scale = 4)
    private BigDecimal expectedBalance;

    @Column(name = "actual_balance", precision = 19, scale = 4)
    private BigDecimal actualBalance;

    @Column(name = "discrepancy_amount", precision = 19, scale = 4)
    private BigDecimal discrepancyAmount;

    @Column(name = "discrepancy_count")
    @Builder.Default
    private Integer discrepancyCount = 0;

    @Column(name = "transactions_matched")
    @Builder.Default
    private Integer transactionsMatched = 0;

    @Column(name = "transactions_unmatched")
    @Builder.Default
    private Integer transactionsUnmatched = 0;

    @Column(name = "auto_corrected_count")
    @Builder.Default
    private Integer autoCorrectedCount = 0;

    @Column(name = "manual_review_required")
    @Builder.Default
    private Boolean manualReviewRequired = false;

    @Column(name = "compliance_validated")
    @Builder.Default
    private Boolean complianceValidated = false;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "reconciled_by", length = 100)
    private String reconciledBy;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    @Column(name = "version")
    private Long version;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
