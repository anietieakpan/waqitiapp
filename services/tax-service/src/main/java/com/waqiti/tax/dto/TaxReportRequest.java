package com.waqiti.tax.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Request object for generating tax reports.
 * Used to specify parameters for tax reporting across different time periods
 * and criteria for compliance and audit purposes.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReportRequest {

    /**
     * User ID for whom the tax report is being generated
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Start date of the reporting period
     */
    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * End date of the reporting period
     */
    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * Tax year for the report
     */
    private Integer taxYear;

    /**
     * Specific jurisdictions to include (empty means all)
     */
    private List<String> jurisdictions;

    /**
     * Transaction types to include in the report
     */
    private List<String> transactionTypes;

    /**
     * Report format (PDF, CSV, JSON, XML)
     */
    @Builder.Default
    private String reportFormat = "JSON";

    /**
     * Whether to include detailed transaction breakdown
     */
    @Builder.Default
    private Boolean includeTransactionDetails = false;

    /**
     * Whether to include tax rule references
     */
    @Builder.Default
    private Boolean includeTaxRuleReferences = false;

    /**
     * Whether to include exemptions and deductions
     */
    @Builder.Default
    private Boolean includeExemptionsAndDeductions = true;

    /**
     * Minimum transaction amount to include
     */
    private java.math.BigDecimal minimumAmount;

    /**
     * Maximum transaction amount to include
     */
    private java.math.BigDecimal maximumAmount;

    /**
     * Currency for the report (filters transactions)
     */
    private String currency;

    /**
     * Report type (SUMMARY, DETAILED, COMPLIANCE, AUDIT)
     */
    @Builder.Default
    private String reportType = "SUMMARY";

    /**
     * Whether to group results by jurisdiction
     */
    @Builder.Default
    private Boolean groupByJurisdiction = false;

    /**
     * Whether to group results by transaction type
     */
    @Builder.Default
    private Boolean groupByTransactionType = false;

    /**
     * Whether to group results by month
     */
    @Builder.Default
    private Boolean groupByMonth = false;

    /**
     * Additional filters for specific tax types
     */
    private List<String> taxTypeFilters;

    /**
     * Whether to include only taxable transactions
     */
    @Builder.Default
    private Boolean taxableTransactionsOnly = false;

    /**
     * Language for the report
     */
    @Builder.Default
    private String language = "en";

    /**
     * Time zone for date/time formatting
     */
    @Builder.Default
    private String timeZone = "UTC";

    /**
     * Validates the request parameters
     */
    public void validate() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        
        if (minimumAmount != null && maximumAmount != null && 
            minimumAmount.compareTo(maximumAmount) > 0) {
            throw new IllegalArgumentException("Minimum amount must be less than or equal to maximum amount");
        }
        
        if (taxYear != null && (taxYear < 2000 || taxYear > LocalDate.now().getYear() + 1)) {
            throw new IllegalArgumentException("Invalid tax year");
        }
        
        List<String> validFormats = List.of("JSON", "PDF", "CSV", "XML");
        if (reportFormat != null && !validFormats.contains(reportFormat.toUpperCase())) {
            throw new IllegalArgumentException("Invalid report format. Must be one of: " + validFormats);
        }
        
        List<String> validTypes = List.of("SUMMARY", "DETAILED", "COMPLIANCE", "AUDIT");
        if (reportType != null && !validTypes.contains(reportType.toUpperCase())) {
            throw new IllegalArgumentException("Invalid report type. Must be one of: " + validTypes);
        }
    }

    /**
     * Gets the effective tax year (derived from dates if not specified)
     */
    public int getEffectiveTaxYear() {
        if (taxYear != null) {
            return taxYear;
        }
        
        if (endDate != null) {
            return endDate.getYear();
        }
        
        return LocalDate.now().getYear();
    }

    /**
     * Checks if this is a year-end report
     */
    public boolean isYearEndReport() {
        return startDate != null && endDate != null &&
               startDate.getMonth() == java.time.Month.JANUARY && startDate.getDayOfMonth() == 1 &&
               endDate.getMonth() == java.time.Month.DECEMBER && endDate.getDayOfMonth() == 31;
    }

    /**
     * Checks if this is a quarterly report
     */
    public boolean isQuarterlyReport() {
        if (startDate == null || endDate == null) {
            return false;
        }
        
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return daysBetween >= 89 && daysBetween <= 92; // Approximately 3 months
    }

    /**
     * Gets the reporting period as a formatted string
     */
    public String getReportingPeriod() {
        if (startDate == null || endDate == null) {
            return "Unknown Period";
        }
        
        if (startDate.equals(endDate)) {
            return startDate.toString();
        }
        
        return String.format("%s to %s", startDate, endDate);
    }

    /**
     * Checks if detailed breakdown is requested
     */
    public boolean requiresDetailedBreakdown() {
        return Boolean.TRUE.equals(includeTransactionDetails) || 
               Boolean.TRUE.equals(groupByJurisdiction) ||
               Boolean.TRUE.equals(groupByTransactionType) ||
               Boolean.TRUE.equals(groupByMonth);
    }
}