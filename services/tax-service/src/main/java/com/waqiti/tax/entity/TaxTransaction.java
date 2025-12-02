package com.waqiti.tax.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity representing calculated tax information for financial transactions.
 * Stores comprehensive tax calculation results for compliance and reporting.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Entity
@Table(name = "tax_transactions", indexes = {
    @Index(name = "idx_tax_transaction_user", columnList = "user_id"),
    @Index(name = "idx_tax_transaction_jurisdiction", columnList = "jurisdiction"),
    @Index(name = "idx_tax_transaction_date", columnList = "calculation_date"),
    @Index(name = "idx_tax_transaction_year", columnList = "tax_year"),
    @Index(name = "idx_tax_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_tax_transaction_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier of the financial transaction
     */
    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    /**
     * User ID who performed the transaction
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * Tax jurisdiction where tax was calculated
     */
    @Column(name = "jurisdiction", nullable = false, length = 10)
    private String jurisdiction;

    /**
     * Type of the original transaction
     */
    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    /**
     * Original transaction amount (before tax)
     */
    @Column(name = "transaction_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal transactionAmount;

    /**
     * Calculated tax amount
     */
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    /**
     * Currency code for all amounts
     */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    /**
     * Effective tax rate applied (as percentage)
     */
    @Column(name = "effective_tax_rate", precision = 8, scale = 4)
    private BigDecimal effectiveTaxRate;

    /**
     * Detailed breakdown of taxes by type (JSON format)
     */
    @Type(type = "json")
    @Column(name = "tax_breakdown", columnDefinition = "JSON")
    private Map<String, BigDecimal> taxBreakdown;

    /**
     * Date when tax was calculated
     */
    @Column(name = "calculation_date", nullable = false)
    private LocalDateTime calculationDate;

    /**
     * Tax year this transaction belongs to
     */
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    /**
     * Status of tax calculation
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "CALCULATED";

    /**
     * List of tax rule IDs that were applied
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tax_transaction_rules", joinColumns = @JoinColumn(name = "tax_transaction_id"))
    @Column(name = "rule_id")
    private java.util.List<Long> appliedRuleIds;

    /**
     * List of exemption codes that were applied
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tax_transaction_exemptions", joinColumns = @JoinColumn(name = "tax_transaction_id"))
    @Column(name = "exemption_code")
    private java.util.List<String> exemptionCodes;

    /**
     * List of deduction codes that were applied
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tax_transaction_deductions", joinColumns = @JoinColumn(name = "tax_transaction_id"))
    @Column(name = "deduction_code")
    private java.util.List<String> deductionCodes;

    /**
     * Total exemption amount
     */
    @Column(name = "exemption_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal exemptionAmount = BigDecimal.ZERO;

    /**
     * Total deduction amount
     */
    @Column(name = "deduction_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    /**
     * Whether manual review was required
     */
    @Column(name = "manual_review_required")
    @Builder.Default
    private Boolean manualReviewRequired = false;

    /**
     * Confidence score of the tax calculation (0.0 to 1.0)
     */
    @Column(name = "confidence_score", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal confidenceScore = new BigDecimal("1.00");

    /**
     * Cross-border transaction indicator
     */
    @Column(name = "cross_border")
    @Builder.Default
    private Boolean crossBorder = false;

    /**
     * Source country for cross-border transactions
     */
    @Column(name = "source_country", length = 3)
    private String sourceCountry;

    /**
     * Destination country for cross-border transactions
     */
    @Column(name = "destination_country", length = 3)
    private String destinationCountry;

    /**
     * Business category for business transactions
     */
    @Column(name = "business_category", length = 50)
    private String businessCategory;

    /**
     * Customer type (INDIVIDUAL, BUSINESS, GOVERNMENT, etc.)
     */
    @Column(name = "customer_type", length = 20)
    private String customerType;

    /**
     * Payment method used
     */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /**
     * Reference to the original transaction if this is a refund
     */
    @Column(name = "original_transaction_id", length = 50)
    private String originalTransactionId;

    /**
     * Notes or comments about the tax calculation
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Additional metadata (JSON format)
     */
    @Type(type = "json")
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, String> metadata;

    /**
     * Whether this transaction has been included in tax filing
     */
    @Column(name = "included_in_filing")
    @Builder.Default
    private Boolean includedInFiling = false;

    /**
     * Tax filing reference ID if included in filing
     */
    @Column(name = "filing_reference", length = 100)
    private String filingReference;

    /**
     * Date when transaction was included in filing
     */
    @Column(name = "filing_date")
    private LocalDateTime filingDate;

    /**
     * Last review date for compliance
     */
    @Column(name = "last_review_date")
    private LocalDateTime lastReviewDate;

    /**
     * Next review date for compliance
     */
    @Column(name = "next_review_date")
    private LocalDateTime nextReviewDate;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Created timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated timestamp
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who created this record
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * User who last updated this record
     */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /**
     * Calculates the total amount including tax
     */
    public BigDecimal getTotalAmountWithTax() {
        return transactionAmount != null && taxAmount != null ? 
            transactionAmount.add(taxAmount) : null;
    }

    /**
     * Calculates the net taxable amount (after deductions)
     */
    public BigDecimal getNetTaxableAmount() {
        if (transactionAmount == null) {
            return null;
        }
        
        BigDecimal net = transactionAmount;
        
        if (deductionAmount != null) {
            net = net.subtract(deductionAmount);
        }
        
        return net.max(BigDecimal.ZERO);
    }

    /**
     * Gets the effective tax savings from exemptions and deductions
     */
    public BigDecimal getTaxSavings() {
        BigDecimal savings = BigDecimal.ZERO;
        
        if (exemptionAmount != null) {
            savings = savings.add(exemptionAmount);
        }
        
        if (deductionAmount != null && effectiveTaxRate != null) {
            // Approximate tax savings from deductions
            BigDecimal deductionSavings = deductionAmount
                .multiply(effectiveTaxRate.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
            savings = savings.add(deductionSavings);
        }
        
        return savings;
    }

    /**
     * Checks if this transaction requires compliance filing
     */
    public boolean requiresComplianceFiling() {
        // Large transactions or cross-border transactions typically require filing
        if (Boolean.TRUE.equals(crossBorder)) {
            return true;
        }
        
        if (transactionAmount != null && transactionAmount.compareTo(new BigDecimal("10000")) > 0) {
            return true;
        }
        
        return Boolean.TRUE.equals(manualReviewRequired);
    }

    /**
     * Checks if this transaction is overdue for review
     */
    public boolean isOverdueForReview() {
        if (nextReviewDate == null) {
            return false;
        }
        
        return LocalDateTime.now().isAfter(nextReviewDate);
    }

    /**
     * Gets a formatted display of the tax amount
     */
    public String getFormattedTaxAmount() {
        return taxAmount != null ? 
            String.format("%.2f %s", taxAmount, currency) : "N/A";
    }

    /**
     * Gets a formatted display of the effective tax rate
     */
    public String getFormattedEffectiveRate() {
        return effectiveTaxRate != null ? 
            String.format("%.4f%%", effectiveTaxRate) : "N/A";
    }

    /**
     * Marks this transaction as included in filing
     */
    public void markAsIncludedInFiling(String filingRef) {
        this.includedInFiling = true;
        this.filingReference = filingRef;
        this.filingDate = LocalDateTime.now();
    }

    /**
     * Schedules next review based on transaction characteristics
     */
    public void scheduleNextReview() {
        LocalDateTime now = LocalDateTime.now();
        
        if (Boolean.TRUE.equals(crossBorder) || Boolean.TRUE.equals(manualReviewRequired)) {
            // High-risk transactions need quarterly review
            this.nextReviewDate = now.plusMonths(3);
        } else if (taxAmount != null && taxAmount.compareTo(new BigDecimal("1000")) > 0) {
            // Medium transactions need semi-annual review
            this.nextReviewDate = now.plusMonths(6);
        } else {
            // Low-risk transactions need annual review
            this.nextReviewDate = now.plusYears(1);
        }
    }

    /**
     * Validates the tax transaction data
     */
    public void validate() {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction amount must be non-negative");
        }
        
        if (taxAmount == null || taxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tax amount must be non-negative");
        }
        
        if (taxYear == null || taxYear < 2000 || taxYear > java.time.LocalDate.now().getYear() + 1) {
            throw new IllegalArgumentException("Invalid tax year");
        }
        
        if (Boolean.TRUE.equals(crossBorder) && (sourceCountry == null || destinationCountry == null)) {
            throw new IllegalArgumentException("Source and destination countries required for cross-border transactions");
        }
    }
}