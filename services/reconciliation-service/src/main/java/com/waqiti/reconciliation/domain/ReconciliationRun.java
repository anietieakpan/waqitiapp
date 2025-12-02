package com.waqiti.reconciliation.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs", indexes = {
    @Index(name = "idx_run_date", columnList = "runDate"),
    @Index(name = "idx_run_type", columnList = "runType"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_account_number", columnList = "accountNumber"),
    @Index(name = "idx_reconciliation_period", columnList = "reconciliationPeriodStart, reconciliationPeriodEnd")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReconciliationRun extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "run_name", nullable = false, length = 255)
    private String runName;
    
    @Column(name = "run_date", nullable = false)
    private LocalDateTime runDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false)
    private RunType runType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency;
    
    @Column(name = "account_number", length = 50)
    private String accountNumber;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "reconciliation_period_start", nullable = false)
    private LocalDateTime reconciliationPeriodStart;
    
    @Column(name = "reconciliation_period_end", nullable = false)
    private LocalDateTime reconciliationPeriodEnd;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatus status;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    
    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;
    
    @Column(name = "total_internal_entries")
    private Long totalInternalEntries;
    
    @Column(name = "total_external_entries")
    private Long totalExternalEntries;
    
    @Column(name = "matched_entries")
    private Long matchedEntries;
    
    @Column(name = "unmatched_internal_entries")
    private Long unmatchedInternalEntries;
    
    @Column(name = "unmatched_external_entries")
    private Long unmatchedExternalEntries;
    
    @Column(name = "exception_count")
    private Long exceptionCount;
    
    @Column(name = "total_internal_amount", precision = 19, scale = 4)
    private BigDecimal totalInternalAmount;
    
    @Column(name = "total_external_amount", precision = 19, scale = 4)
    private BigDecimal totalExternalAmount;
    
    @Column(name = "matched_amount", precision = 19, scale = 4)
    private BigDecimal matchedAmount;
    
    @Column(name = "variance_amount", precision = 19, scale = 4)
    private BigDecimal varianceAmount;
    
    @Column(name = "tolerance_percentage", precision = 5, scale = 4)
    private BigDecimal tolerancePercentage;
    
    @Column(name = "tolerance_amount", precision = 19, scale = 4)
    private BigDecimal toleranceAmount;
    
    @Column(name = "reconciliation_percentage", precision = 5, scale = 2)
    private BigDecimal reconciliationPercentage;
    
    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance;
    
    @Column(name = "closing_balance_internal", precision = 19, scale = 4)
    private BigDecimal closingBalanceInternal;
    
    @Column(name = "closing_balance_external", precision = 19, scale = 4)
    private BigDecimal closingBalanceExternal;
    
    @Column(name = "balance_variance", precision = 19, scale = 4)
    private BigDecimal balanceVariance;
    
    @Column(name = "is_balanced", nullable = false)
    private Boolean isBalanced = false;
    
    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;
    
    @Column(name = "approved_by", length = 100)
    private String approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "config_snapshot", columnDefinition = "TEXT")
    private String configSnapshot;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_run_statistics", 
                     joinColumns = @JoinColumn(name = "run_id"))
    @MapKeyColumn(name = "stat_key")
    @Column(name = "stat_value", columnDefinition = "TEXT")
    private Map<String, String> statistics;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_run_parameters", 
                     joinColumns = @JoinColumn(name = "run_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value", columnDefinition = "TEXT")
    private Map<String, String> parameters;
    
    public enum RunType {
        DAILY_RECONCILIATION,
        INTRADAY_RECONCILIATION,
        MONTH_END_RECONCILIATION,
        YEAR_END_RECONCILIATION,
        NOSTRO_RECONCILIATION,
        VOSTRO_RECONCILIATION,
        GL_RECONCILIATION,
        PAYMENT_RECONCILIATION,
        SETTLEMENT_RECONCILIATION,
        CLEARING_RECONCILIATION,
        MANUAL_RECONCILIATION,
        EXCEPTION_INVESTIGATION,
        VARIANCE_ANALYSIS
    }
    
    public enum Frequency {
        REAL_TIME,
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        ANNUALLY,
        ON_DEMAND
    }
    
    public enum RunStatus {
        SCHEDULED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        PARTIALLY_COMPLETED,
        REQUIRES_INTERVENTION
    }
    
    public boolean isCompleted() {
        return status == RunStatus.COMPLETED || status == RunStatus.APPROVED;
    }
    
    public boolean hasVariances() {
        return varianceAmount != null && varianceAmount.compareTo(BigDecimal.ZERO) != 0;
    }
    
    public boolean hasExceptions() {
        return exceptionCount != null && exceptionCount > 0;
    }
    
    public BigDecimal getMatchingRate() {
        if (totalInternalEntries == null || totalInternalEntries == 0) {
            return BigDecimal.ZERO;
        }
        
        long totalMatched = matchedEntries != null ? matchedEntries : 0;
        return BigDecimal.valueOf(totalMatched)
            .divide(BigDecimal.valueOf(totalInternalEntries), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
    
    public boolean requiresReview() {
        return hasVariances() || hasExceptions() || !isBalanced || 
               status == RunStatus.REQUIRES_INTERVENTION;
    }
    
    public void calculateDuration() {
        if (startedAt != null && completedAt != null) {
            durationSeconds = java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
    }
    
    public void calculateReconciliationPercentage() {
        if (totalInternalEntries != null && totalInternalEntries > 0) {
            long totalMatched = matchedEntries != null ? matchedEntries : 0;
            reconciliationPercentage = BigDecimal.valueOf(totalMatched)
                .divide(BigDecimal.valueOf(totalInternalEntries), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        } else {
            reconciliationPercentage = BigDecimal.ZERO;
        }
    }
}