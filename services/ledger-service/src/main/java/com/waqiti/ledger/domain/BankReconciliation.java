package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bank Reconciliation Entity
 * 
 * Stores completed bank reconciliations for audit trail and history tracking.
 * Critical for financial reporting and compliance.
 */
@Entity
@Table(name = "bank_reconciliations",
    indexes = {
        @Index(name = "idx_bank_account_id", columnList = "bank_account_id"),
        @Index(name = "idx_reconciliation_date", columnList = "reconciliation_date"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "created_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_date", columnNames = {"bank_account_id", "reconciliation_date"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "reconciliation_id", updatable = false, nullable = false)
    private UUID reconciliationId;
    
    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;
    
    @Column(name = "bank_account_name", nullable = false)
    private String bankAccountName;
    
    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;
    
    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;
    
    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    // Balance Information
    @Column(name = "book_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal bookBalance;
    
    @Column(name = "bank_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal bankBalance;
    
    @Column(name = "reconciled_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal reconciledBalance;
    
    @Column(name = "variance", precision = 19, scale = 4, nullable = false)
    private BigDecimal variance;
    
    // Outstanding Items Summary
    @Column(name = "outstanding_checks_count")
    private Integer outstandingChecksCount;
    
    @Column(name = "outstanding_checks_amount", precision = 19, scale = 4)
    private BigDecimal outstandingChecksAmount;
    
    @Column(name = "deposits_in_transit_count")
    private Integer depositsInTransitCount;
    
    @Column(name = "deposits_in_transit_amount", precision = 19, scale = 4)
    private BigDecimal depositsInTransitAmount;
    
    @Column(name = "other_items_count")
    private Integer otherItemsCount;
    
    @Column(name = "other_items_amount", precision = 19, scale = 4)
    private BigDecimal otherItemsAmount;
    
    // Matching Statistics
    @Column(name = "total_statement_items")
    private Integer totalStatementItems;
    
    @Column(name = "matched_items")
    private Integer matchedItems;
    
    @Column(name = "unmatched_items")
    private Integer unmatchedItems;
    
    @Column(name = "matching_rate", precision = 5, scale = 2)
    private BigDecimal matchingRate;
    
    // Status and Audit
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconciliationStatus status;
    
    @Column(name = "reconciled", nullable = false)
    private Boolean reconciled;
    
    @Column(name = "reconciled_by", nullable = false)
    private String reconciledBy;
    
    @Column(name = "reconciled_at", nullable = false)
    private LocalDateTime reconciledAt;
    
    @Column(name = "approved_by")
    private String approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "variance_explanation", columnDefinition = "TEXT")
    private String varianceExplanation;
    
    // Processing Metrics
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "auto_matched_percentage", precision = 5, scale = 2)
    private BigDecimal autoMatchedPercentage;
    
    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum ReconciliationStatus {
        DRAFT,
        IN_PROGRESS,
        COMPLETED,
        APPROVED,
        REJECTED,
        ARCHIVED
    }
}