package com.waqiti.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Comprehensive reconciliation summary DTO with matching statistics and discrepancy analysis.
 * Provides detailed insights into reconciliation results and quality metrics.
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationSummary {
    
    /**
     * Unique reconciliation ID.
     */
    @JsonProperty("reconciliation_id")
    private String reconciliationId;
    
    /**
     * Account information being reconciled.
     */
    @NotNull(message = "Account information cannot be null")
    @JsonProperty("account_info")
    private AccountInfo accountInfo;
    
    /**
     * Balance reconciliation summary.
     */
    @NotNull(message = "Balance summary cannot be null")
    @JsonProperty("balance_summary")
    private BalanceSummary balanceSummary;
    
    /**
     * Reconciliation processing status.
     */
    @NotNull(message = "Status information cannot be null")
    @JsonProperty("status_info")
    private StatusInfo statusInfo;
    
    /**
     * Outstanding items analysis.
     */
    @JsonProperty("outstanding_items")
    private OutstandingItemsSummary outstandingItems;
    
    /**
     * Transaction matching statistics.
     */
    @JsonProperty("matching_statistics")
    private MatchingStatistics matchingStatistics;
    
    /**
     * Discrepancy analysis and breakdown.
     */
    @JsonProperty("discrepancy_analysis")
    private DiscrepancyAnalysis discrepancyAnalysis;
    
    /**
     * Quality and performance metrics.
     */
    @JsonProperty("quality_metrics")
    private QualityMetrics qualityMetrics;
    
    /**
     * Audit and workflow information.
     */
    @JsonProperty("audit_info")
    private AuditInfo auditInfo;
    
    /**
     * Account information for reconciliation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountInfo {
        
        @JsonProperty("account_name")
        private String accountName;
        
        @JsonProperty("account_number")
        private String accountNumber;
        
        @JsonProperty("account_code")
        private String accountCode;
        
        @JsonProperty("bank_name")
        private String bankName;
        
        private String currency;
        
        @JsonProperty("account_type")
        private String accountType;
    }
    
    /**
     * Balance reconciliation summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceSummary {
        
        /**
         * Book balance from general ledger.
         */
        @JsonProperty("book_balance")
        private BigDecimal bookBalance;
        
        /**
         * Bank balance from statement.
         */
        @JsonProperty("bank_balance")
        private BigDecimal bankBalance;
        
        /**
         * Adjusted book balance after reconciling items.
         */
        @JsonProperty("adjusted_book_balance")
        private BigDecimal adjustedBookBalance;
        
        /**
         * Adjusted bank balance after reconciling items.
         */
        @JsonProperty("adjusted_bank_balance")
        private BigDecimal adjustedBankBalance;
        
        /**
         * Final reconciled balance.
         */
        @JsonProperty("reconciled_balance")
        private BigDecimal reconciledBalance;
        
        /**
         * Variance between book and bank balances.
         */
        private BigDecimal variance;
        
        /**
         * Absolute variance amount.
         */
        @JsonProperty("absolute_variance")
        private BigDecimal absoluteVariance;
        
        /**
         * Variance as percentage of book balance.
         */
        @JsonProperty("variance_percentage")
        private BigDecimal variancePercentage;
        
        /**
         * Whether the variance is within acceptable tolerance.
         */
        @JsonProperty("within_tolerance")
        private Boolean withinTolerance;
    }
    
    /**
     * Reconciliation status and workflow information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusInfo {
        
        /**
         * Whether reconciliation is balanced.
         */
        private Boolean reconciled;
        
        /**
         * Current reconciliation status.
         */
        @JsonProperty("reconciliation_status")
        @Builder.Default
        private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;
        
        /**
         * Overall result of reconciliation.
         */
        @JsonProperty("reconciliation_result")
        private ReconciliationResult reconciliationResult;
        
        /**
         * When reconciliation was completed.
         */
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("completed_at")
        private LocalDateTime completedAt;
        
        /**
         * Processing duration in seconds.
         */
        @JsonProperty("processing_duration_seconds")
        private Long processingDurationSeconds;
        
        /**
         * Whether manager review is required.
         */
        @JsonProperty("requires_manager_review")
        private Boolean requiresManagerReview;
        
        /**
         * Next recommended action.
         */
        @JsonProperty("next_action")
        private String nextAction;
    }
    
    /**
     * Reconciliation status enumeration.
     */
    public enum ReconciliationStatus {
        PENDING("Reconciliation in progress"),
        BALANCED("Reconciliation is balanced"),
        UNBALANCED("Reconciliation has variances"),
        PENDING_REVIEW("Pending management review"),
        APPROVED("Reconciliation approved"),
        REJECTED("Reconciliation rejected"),
        CANCELLED("Reconciliation cancelled");
        
        private final String description;
        
        ReconciliationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Reconciliation result enumeration.
     */
    public enum ReconciliationResult {
        SUCCESS("Reconciliation completed successfully"),
        SUCCESS_WITH_ADJUSTMENTS("Reconciliation completed with adjustments"),
        FAILED_MATERIAL_VARIANCE("Failed due to material variance"),
        FAILED_TIMEOUT("Failed due to processing timeout"),
        FAILED_DATA_QUALITY("Failed due to poor data quality");
        
        private final String description;
        
        ReconciliationResult(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Outstanding items summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutstandingItemsSummary {
        
        @JsonProperty("total_outstanding_items")
        private Integer totalOutstandingItems;
        
        @JsonProperty("total_outstanding_amount")
        private BigDecimal totalOutstandingAmount;
        
        @JsonProperty("outstanding_checks_count")
        private Integer outstandingChecksCount;
        
        @JsonProperty("outstanding_checks_amount")
        private BigDecimal outstandingChecksAmount;
        
        @JsonProperty("deposits_in_transit_count")
        private Integer depositsInTransitCount;
        
        @JsonProperty("deposits_in_transit_amount")
        private BigDecimal depositsInTransitAmount;
        
        @JsonProperty("bank_charges_count")
        private Integer bankChargesCount;
        
        @JsonProperty("bank_charges_amount")
        private BigDecimal bankChargesAmount;
        
        @JsonProperty("other_adjustments_count")
        private Integer otherAdjustmentsCount;
        
        @JsonProperty("other_adjustments_amount")
        private BigDecimal otherAdjustmentsAmount;
        
        /**
         * Age analysis of outstanding items.
         */
        @JsonProperty("age_analysis")
        private AgeAnalysis ageAnalysis;
    }
    
    /**
     * Age analysis for outstanding items.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgeAnalysis {
        
        @JsonProperty("current_count")
        private Integer currentCount;
        
        @JsonProperty("current_amount")
        private BigDecimal currentAmount;
        
        @JsonProperty("days_1_30_count")
        private Integer days1To30Count;
        
        @JsonProperty("days_1_30_amount")
        private BigDecimal days1To30Amount;
        
        @JsonProperty("days_31_60_count")
        private Integer days31To60Count;
        
        @JsonProperty("days_31_60_amount")
        private BigDecimal days31To60Amount;
        
        @JsonProperty("days_61_90_count")
        private Integer days61To90Count;
        
        @JsonProperty("days_61_90_amount")
        private BigDecimal days61To90Amount;
        
        @JsonProperty("over_90_days_count")
        private Integer over90DaysCount;
        
        @JsonProperty("over_90_days_amount")
        private BigDecimal over90DaysAmount;
    }
    
    /**
     * Transaction matching statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchingStatistics {
        
        @JsonProperty("total_transactions_processed")
        private Integer totalTransactionsProcessed;
        
        @JsonProperty("total_transactions_matched")
        private Integer totalTransactionsMatched;
        
        @JsonProperty("total_transactions_unmatched")
        private Integer totalTransactionsUnmatched;
        
        @JsonProperty("automatic_matches")
        private Integer automaticMatches;
        
        @JsonProperty("manual_matches")
        private Integer manualMatches;
        
        @JsonProperty("matching_percentage")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private BigDecimal matchingPercentage;
        
        @JsonProperty("average_match_confidence")
        private BigDecimal averageMatchConfidence;
        
        @JsonProperty("high_confidence_matches")
        private Integer highConfidenceMatches;
        
        @JsonProperty("low_confidence_matches")
        private Integer lowConfidenceMatches;
        
        @JsonProperty("partial_matches")
        private Integer partialMatches;
    }
    
    /**
     * Discrepancy analysis and breakdown.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyAnalysis {
        
        @JsonProperty("total_discrepancies")
        private Integer totalDiscrepancies;
        
        @JsonProperty("total_discrepancy_amount")
        private BigDecimal totalDiscrepancyAmount;
        
        @JsonProperty("material_discrepancies")
        private Integer materialDiscrepancies;
        
        @JsonProperty("resolved_discrepancies")
        private Integer resolvedDiscrepancies;
        
        @JsonProperty("pending_discrepancies")
        private Integer pendingDiscrepancies;
        
        /**
         * Discrepancies by type.
         */
        @JsonProperty("discrepancy_breakdown")
        @Builder.Default
        private java.util.Map<String, DiscrepancyTypeBreakdown> discrepancyBreakdown = new java.util.HashMap<>();
        
        /**
         * Root cause analysis.
         */
        @JsonProperty("root_causes")
        @Builder.Default
        private java.util.List<String> rootCauses = new java.util.ArrayList<>();
    }
    
    /**
     * Discrepancy breakdown by type.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyTypeBreakdown {
        
        private Integer count;
        
        private BigDecimal amount;
        
        private String description;
        
        @JsonProperty("resolution_status")
        private String resolutionStatus;
    }
    
    /**
     * Quality and performance metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics {
        
        /**
         * Overall data quality score.
         */
        @JsonProperty("data_quality_score")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private BigDecimal dataQualityScore;
        
        /**
         * Data quality level.
         */
        @JsonProperty("data_quality_level")
        private DataQualityLevel dataQualityLevel;
        
        /**
         * Completeness percentage.
         */
        @JsonProperty("completeness_percentage")
        private BigDecimal completenessPercentage;
        
        /**
         * Accuracy percentage.
         */
        @JsonProperty("accuracy_percentage")
        private BigDecimal accuracyPercentage;
        
        /**
         * Processing efficiency score.
         */
        @JsonProperty("efficiency_score")
        private BigDecimal efficiencyScore;
        
        /**
         * Quality issues identified.
         */
        @JsonProperty("quality_issues")
        @Builder.Default
        private java.util.List<String> qualityIssues = new java.util.ArrayList<>();
        
        /**
         * Improvement recommendations.
         */
        @Builder.Default
        private java.util.List<String> recommendations = new java.util.ArrayList<>();
    }
    
    /**
     * Data quality levels.
     */
    public enum DataQualityLevel {
        EXCELLENT("Data quality is excellent"),
        GOOD("Data quality is good"),
        FAIR("Data quality is fair - some issues present"),
        POOR("Data quality is poor - significant issues"),
        CRITICAL("Data quality is critical - major issues");
        
        private final String description;
        
        DataQualityLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Audit and workflow information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditInfo {
        
        @JsonProperty("performed_by")
        private String performedBy;
        
        @JsonProperty("reviewed_by")
        private String reviewedBy;
        
        @JsonProperty("approved_by")
        private String approvedBy;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("started_at")
        private LocalDateTime startedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("completed_at")
        private LocalDateTime completedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("approved_at")
        private LocalDateTime approvedAt;
        
        @JsonProperty("reconciliation_method")
        private String reconciliationMethod;
        
        @JsonProperty("approval_required")
        private Boolean approvalRequired;
        
        @JsonProperty("approval_threshold")
        private BigDecimal approvalThreshold;
        
        private String comments;
        
        @JsonProperty("supporting_documents")
        @Builder.Default
        private java.util.List<String> supportingDocuments = new java.util.ArrayList<>();
    }
    
    /**
     * Checks if reconciliation is within acceptable variance tolerance.
     *
     * @return true if within tolerance
     */
    public boolean isWithinTolerance() {
        return balanceSummary != null && Boolean.TRUE.equals(balanceSummary.withinTolerance);
    }
    
    /**
     * Checks if reconciliation requires management attention.
     *
     * @return true if requires manager review
     */
    public boolean requiresManagementAttention() {
        return statusInfo != null && Boolean.TRUE.equals(statusInfo.requiresManagerReview);
    }
    
    /**
     * Gets the overall reconciliation success rate.
     *
     * @return success rate as percentage
     */
    public BigDecimal getSuccessRate() {
        if (matchingStatistics == null || matchingStatistics.totalTransactionsProcessed == null || 
            matchingStatistics.totalTransactionsProcessed == 0) {
            return BigDecimal.ZERO;
        }
        
        return matchingStatistics.matchingPercentage != null ? 
            matchingStatistics.matchingPercentage : BigDecimal.ZERO;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReconciliationSummary that = (ReconciliationSummary) o;
        return Objects.equals(reconciliationId, that.reconciliationId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(reconciliationId);
    }
    
    @Override
    public String toString() {
        return "ReconciliationSummary{" +
               "reconciliationId='" + reconciliationId + '\'' +
               ", accountNumber='" + (accountInfo != null ? accountInfo.accountNumber : null) + '\'' +
               ", status=" + (statusInfo != null ? statusInfo.reconciliationStatus : null) +
               ", variance=" + (balanceSummary != null ? balanceSummary.variance : null) +
               ", matchingRate=" + getSuccessRate() + "%" +
               '}';
    }
}