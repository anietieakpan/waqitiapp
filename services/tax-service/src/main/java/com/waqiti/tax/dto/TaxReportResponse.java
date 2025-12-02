package com.waqiti.tax.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response object for tax reports.
 * Contains comprehensive tax reporting data including summaries,
 * breakdowns, and detailed transaction information for compliance purposes.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReportResponse {

    /**
     * User ID for whom the report was generated
     */
    private String userId;

    /**
     * Start date of the reporting period
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportPeriodStart;

    /**
     * End date of the reporting period
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportPeriodEnd;

    /**
     * Tax year covered by the report
     */
    private Integer taxYear;

    /**
     * Total transaction amount for the period
     */
    private BigDecimal totalTransactionAmount;

    /**
     * Total tax amount for the period
     */
    private BigDecimal totalTaxAmount;

    /**
     * Effective tax rate for the period
     */
    private BigDecimal effectiveTaxRate;

    /**
     * Tax breakdown by type
     * Key: Tax type (e.g., "SALES_TAX", "VAT", "EXCISE_TAX")
     * Value: Total tax amount for that type
     */
    private Map<String, BigDecimal> taxByType;

    /**
     * Tax breakdown by jurisdiction
     * Key: Jurisdiction code
     * Value: Tax breakdown for that jurisdiction
     */
    private Map<String, TaxJurisdictionSummary> taxByJurisdiction;

    /**
     * Transaction count for the period
     */
    private Integer transactionCount;

    /**
     * Report generation timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedDate;

    /**
     * Report format (JSON, PDF, CSV, XML)
     */
    private String reportFormat;

    /**
     * Report type (SUMMARY, DETAILED, COMPLIANCE, AUDIT)
     */
    private String reportType;

    /**
     * Currency for all monetary amounts
     */
    private String currency;

    /**
     * Monthly breakdown of tax amounts
     */
    private Map<String, BigDecimal> monthlyBreakdown;

    /**
     * Quarterly breakdown of tax amounts
     */
    private Map<String, BigDecimal> quarterlyBreakdown;

    /**
     * Transaction type breakdown
     */
    private Map<String, TransactionTypeSummary> transactionTypeBreakdown;

    /**
     * Total exemptions applied
     */
    private BigDecimal totalExemptions;

    /**
     * Total deductions applied
     */
    private BigDecimal totalDeductions;

    /**
     * Detailed transaction list (if requested)
     */
    private List<TaxTransactionDetail> transactionDetails;

    /**
     * Summary statistics
     */
    private TaxReportStatistics statistics;

    /**
     * Compliance information
     */
    private TaxComplianceInfo complianceInfo;

    /**
     * Warnings and notes
     */
    private List<String> warnings;

    /**
     * Additional notes
     */
    private List<String> notes;

    /**
     * Report metadata
     */
    private Map<String, String> metadata;

    /**
     * Links to supporting documents
     */
    private List<String> supportingDocuments;

    /**
     * Unique report ID
     */
    private String reportId;

    /**
     * Report status
     */
    @Builder.Default
    private String status = "COMPLETED";

    /**
     * Next filing deadline (if applicable)
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextFilingDeadline;

    /**
     * Tax jurisdiction summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxJurisdictionSummary {
        private String jurisdiction;
        private String jurisdictionName;
        private BigDecimal totalTaxAmount;
        private BigDecimal totalTransactionAmount;
        private BigDecimal effectiveTaxRate;
        private Integer transactionCount;
        private Map<String, BigDecimal> taxTypeBreakdown;
        private List<String> applicableRules;
    }

    /**
     * Transaction type summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionTypeSummary {
        private String transactionType;
        private BigDecimal totalTaxAmount;
        private BigDecimal totalTransactionAmount;
        private BigDecimal effectiveTaxRate;
        private Integer transactionCount;
        private BigDecimal averageTransactionAmount;
        private BigDecimal averageTaxAmount;
    }

    /**
     * Detailed transaction information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxTransactionDetail {
        private String transactionId;
        private LocalDate transactionDate;
        private BigDecimal transactionAmount;
        private BigDecimal taxAmount;
        private String transactionType;
        private String jurisdiction;
        private Map<String, BigDecimal> taxBreakdown;
        private List<String> exemptionsApplied;
        private List<String> deductionsApplied;
        private String status;
    }

    /**
     * Report statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxReportStatistics {
        private BigDecimal averageTransactionAmount;
        private BigDecimal averageTaxAmount;
        private BigDecimal medianTransactionAmount;
        private BigDecimal medianTaxAmount;
        private BigDecimal maxTransactionAmount;
        private BigDecimal maxTaxAmount;
        private BigDecimal minTransactionAmount;
        private BigDecimal minTaxAmount;
        private BigDecimal standardDeviation;
        private Integer totalJurisdictions;
        private Integer totalTransactionTypes;
        private Integer exemptedTransactions;
        private BigDecimal exemptedAmount;
    }

    /**
     * Compliance information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxComplianceInfo {
        private Boolean requiresFiling;
        private List<String> filingRequirements;
        private LocalDate nextFilingDeadline;
        private List<String> complianceWarnings;
        private Map<String, String> regulatoryReferences;
        private List<String> recommendedActions;
        private String complianceStatus;
        private LocalDate lastFilingDate;
    }

    /**
     * Gets formatted total tax amount
     */
    public String getFormattedTotalTax() {
        return totalTaxAmount != null && currency != null ? 
            String.format("%.2f %s", totalTaxAmount, currency) : "N/A";
    }

    /**
     * Gets formatted effective tax rate
     */
    public String getFormattedEffectiveRate() {
        return effectiveTaxRate != null ? 
            String.format("%.4f%%", effectiveTaxRate) : "N/A";
    }

    /**
     * Gets the reporting period as a formatted string
     */
    public String getFormattedReportingPeriod() {
        if (reportPeriodStart == null || reportPeriodEnd == null) {
            return "Unknown Period";
        }
        
        if (reportPeriodStart.equals(reportPeriodEnd)) {
            return reportPeriodStart.toString();
        }
        
        return String.format("%s to %s", reportPeriodStart, reportPeriodEnd);
    }

    /**
     * Checks if the report generation was successful
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status);
    }

    /**
     * Checks if there are compliance issues
     */
    public boolean hasComplianceIssues() {
        return complianceInfo != null && 
               complianceInfo.getComplianceWarnings() != null &&
               !complianceInfo.getComplianceWarnings().isEmpty();
    }

    /**
     * Gets the total number of taxable transactions
     */
    public int getTaxableTransactionCount() {
        if (transactionDetails != null) {
            return (int) transactionDetails.stream()
                .filter(tx -> tx.getTaxAmount() != null && tx.getTaxAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();
        }
        return transactionCount != null ? transactionCount : 0;
    }

    /**
     * Calculates the tax burden percentage
     */
    public BigDecimal getTaxBurdenPercentage() {
        if (totalTransactionAmount == null || totalTransactionAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if (totalTaxAmount == null) {
            return BigDecimal.ZERO;
        }
        
        return totalTaxAmount.divide(totalTransactionAmount, 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Adds a warning message
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new java.util.ArrayList<>();
        }
        warnings.add(warning);
    }

    /**
     * Adds a note
     */
    public void addNote(String note) {
        if (notes == null) {
            notes = new java.util.ArrayList<>();
        }
        notes.add(note);
    }
}