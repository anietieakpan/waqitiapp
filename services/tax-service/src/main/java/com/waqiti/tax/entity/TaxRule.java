package com.waqiti.tax.entity;

import com.waqiti.common.encryption.Encrypted;
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
import java.util.List;

/**
 * Entity representing tax rules and regulations for different jurisdictions.
 * Contains comprehensive tax calculation logic and business rules.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Entity
@Table(name = "tax_rules", indexes = {
    @Index(name = "idx_tax_rule_jurisdiction", columnList = "jurisdiction"),
    @Index(name = "idx_tax_rule_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_tax_rule_active", columnList = "active"),
    @Index(name = "idx_tax_rule_effective_date", columnList = "effective_from, effective_to")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique rule code for identification
     */
    @Column(name = "rule_code", unique = true, nullable = false, length = 50)
    private String ruleCode;

    /**
     * Tax jurisdiction (e.g., "US-CA", "US-NY", "UK", "DE")
     */
    @Column(name = "jurisdiction", nullable = false, length = 10)
    private String jurisdiction;

    /**
     * Type of tax (e.g., "SALES_TAX", "VAT", "INCOME_TAX", "EXCISE_TAX")
     */
    @Column(name = "tax_type", nullable = false, length = 50)
    private String taxType;

    /**
     * Transaction type this rule applies to
     */
    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    /**
     * Tax rate as a percentage (e.g., 8.25 for 8.25%)
     */
    @Column(name = "rate", precision = 8, scale = 4)
    private BigDecimal rate;

    /**
     * Calculation type (PERCENTAGE, FLAT_FEE, PROGRESSIVE, BRACKET)
     */
    @Column(name = "calculation_type", nullable = false, length = 20)
    private String calculationType;

    /**
     * Flat fee amount (for FLAT_FEE calculation type)
     */
    @Column(name = "flat_fee", precision = 12, scale = 2)
    private BigDecimal flatFee;

    /**
     * Minimum transaction amount for this rule to apply
     */
    @Column(name = "minimum_amount", precision = 12, scale = 2)
    private BigDecimal minimumAmount;

    /**
     * Maximum transaction amount for this rule to apply
     */
    @Column(name = "maximum_amount", precision = 12, scale = 2)
    private BigDecimal maximumAmount;

    /**
     * Date when this rule becomes effective
     */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /**
     * Date when this rule expires
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Whether this rule is currently active
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Priority of this rule (higher number = higher priority)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Tax brackets for progressive taxation (JSON format)
     */
    @Column(name = "tax_brackets", columnDefinition = "TEXT")
    private String taxBrackets;

    /**
     * List of deduction codes that apply to this rule
     */
    @ElementCollection
    @CollectionTable(name = "tax_rule_deductions", joinColumns = @JoinColumn(name = "tax_rule_id"))
    @Column(name = "deduction_code")
    private List<String> deductions;

    /**
     * Business categories this rule applies to
     */
    @ElementCollection
    @CollectionTable(name = "tax_rule_business_categories", joinColumns = @JoinColumn(name = "tax_rule_id"))
    @Column(name = "business_category")
    private List<String> businessCategories;

    /**
     * Customer types this rule applies to
     */
    @ElementCollection
    @CollectionTable(name = "tax_rule_customer_types", joinColumns = @JoinColumn(name = "tax_rule_id"))
    @Column(name = "customer_type")
    private List<String> customerTypes;

    /**
     * Rule description for documentation
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Legal reference or regulation citation
     */
    @Column(name = "legal_reference", length = 500)
    private String legalReference;

    /**
     * Source of this rule (INTERNAL, EXTERNAL_API, MANUAL)
     */
    @Column(name = "rule_source", length = 20)
    @Builder.Default
    private String ruleSource = "INTERNAL";

    /**
     * Version of this rule for tracking changes
     */
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    /**
     * Configuration parameters for complex rules (JSON format)
     */
    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;

    /**
     * Whether this rule requires manual review
     */
    @Column(name = "requires_manual_review")
    @Builder.Default
    private Boolean requiresManualReview = false;

    /**
     * Confidence level in this rule (0.0 to 1.0)
     */
    @Column(name = "confidence_level", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal confidenceLevel = new BigDecimal("1.00");

    /**
     * Last update timestamp from external source
     */
    @Column(name = "last_external_update")
    private LocalDateTime lastExternalUpdate;

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
     * User who created this rule
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * User who last updated this rule
     */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /**
     * Checks if this rule is currently effective
     */
    public boolean isCurrentlyEffective() {
        if (!Boolean.TRUE.equals(active)) {
            return false;
        }
        
        LocalDate today = LocalDate.now();
        
        if (effectiveFrom != null && today.isBefore(effectiveFrom)) {
            return false;
        }
        
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if this rule applies to a specific transaction amount
     */
    public boolean appliesToAmount(BigDecimal amount) {
        if (minimumAmount != null && amount.compareTo(minimumAmount) < 0) {
            return false;
        }
        
        if (maximumAmount != null && amount.compareTo(maximumAmount) > 0) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if this rule applies to a specific business category
     */
    public boolean appliesToBusinessCategory(String businessCategory) {
        if (businessCategories == null || businessCategories.isEmpty()) {
            return true; // No restrictions means applies to all
        }
        
        return businessCategories.contains(businessCategory);
    }

    /**
     * Checks if this rule applies to a specific customer type
     */
    public boolean appliesToCustomerType(String customerType) {
        if (customerTypes == null || customerTypes.isEmpty()) {
            return true; // No restrictions means applies to all
        }
        
        return customerTypes.contains(customerType);
    }

    /**
     * Gets the effective tax rate for display
     */
    public String getFormattedRate() {
        if (rate != null) {
            return String.format("%.4f%%", rate);
        }
        return "N/A";
    }

    /**
     * Gets a human-readable description of the rule
     */
    public String getDisplayName() {
        return String.format("%s %s (%s)", jurisdiction, taxType, transactionType);
    }

    /**
     * Checks if this rule needs external updates
     */
    public boolean needsExternalUpdate() {
        if (lastExternalUpdate == null) {
            return "EXTERNAL_API".equals(ruleSource);
        }
        
        // Check if it's been more than 24 hours since last update
        return "EXTERNAL_API".equals(ruleSource) && 
               lastExternalUpdate.isBefore(LocalDateTime.now().minusHours(24));
    }

    /**
     * Validates the rule configuration
     */
    public void validate() {
        if (jurisdiction == null || jurisdiction.trim().isEmpty()) {
            throw new IllegalArgumentException("Jurisdiction is required");
        }
        
        if (taxType == null || taxType.trim().isEmpty()) {
            throw new IllegalArgumentException("Tax type is required");
        }
        
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        
        if (calculationType == null || calculationType.trim().isEmpty()) {
            throw new IllegalArgumentException("Calculation type is required");
        }
        
        if ("PERCENTAGE".equals(calculationType) && (rate == null || rate.compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("Rate must be non-negative for percentage calculation");
        }
        
        if ("FLAT_FEE".equals(calculationType) && (flatFee == null || flatFee.compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("Flat fee must be non-negative");
        }
        
        if (minimumAmount != null && maximumAmount != null && 
            minimumAmount.compareTo(maximumAmount) > 0) {
            throw new IllegalArgumentException("Minimum amount cannot be greater than maximum amount");
        }
        
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("Effective from date cannot be after effective to date");
        }
    }
}