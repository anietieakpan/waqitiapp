package com.waqiti.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Discrepancy Record Entity
 *
 * Records discrepancies found during reconciliation processes.
 */
@Entity
@Table(name = "discrepancy_records", indexes = {
    @Index(name = "idx_discrepancy_reconciliation_id", columnList = "reconciliation_id"),
    @Index(name = "idx_discrepancy_account_id", columnList = "account_id"),
    @Index(name = "idx_discrepancy_resolved", columnList = "resolved")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyRecord {

    @Id
    @Column(name = "discrepancy_id", length = 36)
    private String discrepancyId;

    @Column(name = "reconciliation_id", nullable = false, length = 36)
    private String reconciliationId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "discrepancy_type", length = 50)
    private String discrepancyType;

    @Column(name = "expected_value", precision = 19, scale = 4)
    private BigDecimal expectedValue;

    @Column(name = "actual_value", precision = 19, scale = 4)
    private BigDecimal actualValue;

    @Column(name = "difference_amount", precision = 19, scale = 4)
    private BigDecimal differenceAmount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolved")
    @Builder.Default
    private Boolean resolved = false;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    @Column(name = "version")
    private Long version;
}
