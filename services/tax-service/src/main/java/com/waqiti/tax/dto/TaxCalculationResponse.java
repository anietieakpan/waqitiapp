package com.waqiti.tax.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response object for tax calculations.
 * Contains comprehensive tax calculation results including breakdowns,
 * applicable rules, and metadata for compliance and reporting.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationResponse {

    /**
     * Transaction ID for which tax was calculated
     */
    private String transactionId;

    /**
     * Total tax amount calculated
     */
    private BigDecimal totalTaxAmount;

    /**
     * Effective tax rate as a percentage
     */
    private BigDecimal effectiveTaxRate;

    /**
     * Detailed breakdown of taxes by type
     * Key: Tax type (e.g., "SALES_TAX", "VAT", "EXCISE_TAX")
     * Value: Tax amount for that type
     */
    private Map<String, BigDecimal> taxBreakdown;

    /**
     * List of applicable tax rules that were applied
     */
    private List<String> applicableRules;

    /**
     * Timestamp when calculation was performed
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime calculationDate;

    /**
     * Jurisdiction where tax was calculated
     */
    private String jurisdiction;

    /**
     * Currency code for all monetary amounts
     */
    private String currency;

    /**
     * Original transaction amount (before tax)
     */
    private BigDecimal originalAmount;

    /**
     * Total amount including tax
     */
    private BigDecimal totalAmountWithTax;

    /**
     * Any tax exemptions that were applied
     */
    private List<TaxExemptionDetail> exemptionsApplied;

    /**
     * Any deductions that were applied
     */
    private List<TaxDeductionDetail> deductionsApplied;

    /**
     * Warning messages about the calculation
     */
    private List<String> warnings;

    /**
     * Additional information about the calculation
     */
    private List<String> notes;

    /**
     * Tax calculation confidence score (0.0 to 1.0)
     */
    private BigDecimal confidenceScore;

    /**
     * Whether manual review is recommended
     */
    @Builder.Default
    private Boolean requiresManualReview = false;

    /**
     * Tax year for which calculation was performed
     */
    private Integer taxYear;

    /**
     * Unique calculation ID for audit purposes
     */
    private String calculationId;

    /**
     * Status of the tax calculation
     */
    @Builder.Default
    private String status = "COMPLETED";

    /**
     * Error message if calculation failed
     */
    private String errorMessage;

    /**
     * Additional metadata about the calculation
     */
    private Map<String, String> metadata;

    /**
     * Detailed breakdown by jurisdiction (for multi-jurisdiction transactions)
     */
    private Map<String, TaxJurisdictionBreakdown> jurisdictionBreakdown;

    /**
     * Links to relevant tax documents or regulations
     */
    private List<String> regulatoryReferences;

    /**
     * Next review date (for complex calculations)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextReviewDate;

    /**
     * Calculates total amount including tax
     */
    public BigDecimal getTotalAmountWithTax() {
        if (totalAmountWithTax != null) {
            return totalAmountWithTax;
        }
        
        if (originalAmount != null && totalTaxAmount != null) {
            return originalAmount.add(totalTaxAmount);
        }
        
        return null;
    }

    /**
     * Checks if the calculation was successful
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status) && errorMessage == null;
    }

    /**
     * Checks if there are any warnings
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
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

    /**
     * Details about applied tax exemptions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxExemptionDetail {
        private String exemptionCode;
        private String exemptionDescription;
        private BigDecimal exemptionAmount;
        private String exemptionPercentage;
        private String legalBasis;
    }

    /**
     * Details about applied tax deductions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxDeductionDetail {
        private String deductionCode;
        private String deductionDescription;
        private BigDecimal deductionAmount;
        private String deductionPercentage;
        private String category;
    }

    /**
     * Tax breakdown by jurisdiction
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxJurisdictionBreakdown {
        private String jurisdiction;
        private String jurisdictionName;
        private BigDecimal taxAmount;
        private BigDecimal taxRate;
        private Map<String, BigDecimal> taxTypeBreakdown;
        private List<String> applicableRules;
    }

    /**
     * Creates a failed response
     */
    public static TaxCalculationResponse failed(String transactionId, String errorMessage) {
        return TaxCalculationResponse.builder()
            .transactionId(transactionId)
            .status("FAILED")
            .errorMessage(errorMessage)
            .totalTaxAmount(BigDecimal.ZERO)
            .effectiveTaxRate(BigDecimal.ZERO)
            .calculationDate(LocalDateTime.now())
            .build();
    }

    /**
     * Creates a response requiring manual review
     */
    public static TaxCalculationResponse requiresReview(String transactionId, String reason) {
        return TaxCalculationResponse.builder()
            .transactionId(transactionId)
            .status("REQUIRES_REVIEW")
            .requiresManualReview(true)
            .totalTaxAmount(BigDecimal.ZERO)
            .effectiveTaxRate(BigDecimal.ZERO)
            .calculationDate(LocalDateTime.now())
            .build()
            .addNote("Manual review required: " + reason);
    }

    /**
     * Helper method to add a note and return this object for chaining
     */
    private TaxCalculationResponse addNote(String note) {
        addNote(note);
        return this;
    }
}