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
import java.util.List;
import java.util.Map;

/**
 * Entity representing tax jurisdictions and their configuration.
 * Contains jurisdiction-specific tax rules, rates, and compliance requirements.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Entity
@Table(name = "tax_jurisdictions", indexes = {
    @Index(name = "idx_tax_jurisdiction_code", columnList = "code", unique = true),
    @Index(name = "idx_tax_jurisdiction_country", columnList = "country_code"),
    @Index(name = "idx_tax_jurisdiction_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxJurisdiction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique jurisdiction code (e.g., "US-CA", "US-NY", "UK", "DE")
     */
    @Column(name = "code", unique = true, nullable = false, length = 10)
    private String code;

    /**
     * Human-readable name of the jurisdiction
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Country code (ISO 3166-1 alpha-2)
     */
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    /**
     * State/province/region code (if applicable)
     */
    @Column(name = "region_code", length = 10)
    private String regionCode;

    /**
     * Type of jurisdiction (FEDERAL, STATE, PROVINCIAL, MUNICIPAL, LOCAL)
     */
    @Column(name = "jurisdiction_type", nullable = false, length = 20)
    private String jurisdictionType;

    /**
     * Default currency for this jurisdiction
     */
    @Column(name = "default_currency", nullable = false, length = 3)
    @Builder.Default
    private String defaultCurrency = "USD";

    /**
     * Primary tax type used in this jurisdiction
     */
    @Column(name = "primary_tax_type", length = 50)
    private String primaryTaxType;

    /**
     * Maximum tax amount that can be charged
     */
    @Column(name = "max_tax_amount", precision = 12, scale = 2)
    private BigDecimal maxTaxAmount;

    /**
     * Maximum tax rate that can be applied (as percentage)
     */
    @Column(name = "max_tax_rate", precision = 8, scale = 4)
    private BigDecimal maxTaxRate;

    /**
     * Minimum transaction amount subject to tax
     */
    @Column(name = "min_taxable_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minTaxableAmount = BigDecimal.ZERO;

    /**
     * Tax filing threshold amount
     */
    @Column(name = "filing_threshold", precision = 12, scale = 2)
    private BigDecimal filingThreshold;

    /**
     * Supported transaction types for taxation
     */
    @ElementCollection
    @CollectionTable(name = "jurisdiction_transaction_types", joinColumns = @JoinColumn(name = "jurisdiction_id"))
    @Column(name = "transaction_type")
    private List<String> supportedTransactionTypes;

    /**
     * Tax exemption categories available
     */
    @ElementCollection
    @CollectionTable(name = "jurisdiction_exemption_categories", joinColumns = @JoinColumn(name = "jurisdiction_id"))
    @Column(name = "exemption_category")
    private List<String> exemptionCategories;

    /**
     * Compliance requirements for this jurisdiction
     */
    @Type(type = "json")
    @Column(name = "compliance_requirements", columnDefinition = "JSON")
    private Map<String, String> complianceRequirements;

    /**
     * Filing frequencies required (MONTHLY, QUARTERLY, ANNUALLY)
     */
    @ElementCollection
    @CollectionTable(name = "jurisdiction_filing_frequencies", joinColumns = @JoinColumn(name = "jurisdiction_id"))
    @Column(name = "filing_frequency")
    private List<String> filingFrequencies;

    /**
     * Tax calculation precision (decimal places)
     */
    @Column(name = "calculation_precision")
    @Builder.Default
    private Integer calculationPrecision = 2;

    /**
     * Rounding method for tax calculations
     */
    @Column(name = "rounding_method", length = 20)
    @Builder.Default
    private String roundingMethod = "HALF_UP";

    /**
     * Time zone for this jurisdiction
     */
    @Column(name = "time_zone", length = 50)
    @Builder.Default
    private String timeZone = "UTC";

    /**
     * Language code for tax documents
     */
    @Column(name = "language_code", length = 5)
    @Builder.Default
    private String languageCode = "en";

    /**
     * Tax authority name
     */
    @Column(name = "tax_authority", length = 200)
    private String taxAuthority;

    /**
     * Tax authority website URL
     */
    @Column(name = "tax_authority_website", length = 500)
    private String taxAuthorityWebsite;

    /**
     * Contact information for tax authority
     */
    @Column(name = "tax_authority_contact", length = 500)
    private String taxAuthorityContact;

    /**
     * Whether this jurisdiction is currently active
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Whether real-time tax calculation is available
     */
    @Column(name = "real_time_calculation")
    @Builder.Default
    private Boolean realTimeCalculation = true;

    /**
     * Whether external API integration is available
     */
    @Column(name = "external_api_available")
    @Builder.Default
    private Boolean externalApiAvailable = false;

    /**
     * External API endpoint URL
     */
    @Column(name = "external_api_url", length = 500)
    private String externalApiUrl;

    /**
     * External API authentication details (encrypted)
     */
    @Column(name = "external_api_auth", length = 1000)
    private String externalApiAuth;

    /**
     * Last successful external API sync
     */
    @Column(name = "last_external_sync")
    private LocalDateTime lastExternalSync;

    /**
     * Priority for multi-jurisdiction transactions
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Configuration parameters (JSON format)
     */
    @Type(type = "json")
    @Column(name = "configuration", columnDefinition = "JSON")
    private Map<String, String> configuration;

    /**
     * Additional metadata
     */
    @Type(type = "json")
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, String> metadata;

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
     * User who created this jurisdiction
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * User who last updated this jurisdiction
     */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /**
     * Checks if this jurisdiction supports a specific transaction type
     */
    public boolean supportsTransactionType(String transactionType) {
        if (supportedTransactionTypes == null || supportedTransactionTypes.isEmpty()) {
            return true; // No restrictions means all types supported
        }
        
        return supportedTransactionTypes.contains(transactionType);
    }

    /**
     * Checks if an exemption category is available
     */
    public boolean hasExemptionCategory(String category) {
        return exemptionCategories != null && exemptionCategories.contains(category);
    }

    /**
     * Checks if filing is required for a given amount
     */
    public boolean requiresFiling(BigDecimal amount) {
        return filingThreshold != null && amount.compareTo(filingThreshold) >= 0;
    }

    /**
     * Checks if real-time calculation is enabled and available
     */
    public boolean canCalculateRealTime() {
        return Boolean.TRUE.equals(active) && Boolean.TRUE.equals(realTimeCalculation);
    }

    /**
     * Checks if external API integration is available and configured
     */
    public boolean hasExternalApiIntegration() {
        return Boolean.TRUE.equals(externalApiAvailable) && 
               externalApiUrl != null && !externalApiUrl.trim().isEmpty();
    }

    /**
     * Gets the effective calculation precision
     */
    public int getEffectiveCalculationPrecision() {
        return calculationPrecision != null ? calculationPrecision : 2;
    }

    /**
     * Gets the effective rounding method
     */
    public java.math.RoundingMode getEffectiveRoundingMode() {
        if (roundingMethod == null) {
            return java.math.RoundingMode.HALF_UP;
        }
        
        try {
            return java.math.RoundingMode.valueOf(roundingMethod);
        } catch (IllegalArgumentException e) {
            return java.math.RoundingMode.HALF_UP;
        }
    }

    /**
     * Checks if jurisdiction needs external sync
     */
    public boolean needsExternalSync() {
        if (!hasExternalApiIntegration()) {
            return false;
        }
        
        if (lastExternalSync == null) {
            return true;
        }
        
        // Sync daily
        return lastExternalSync.isBefore(LocalDateTime.now().minusHours(24));
    }

    /**
     * Gets display name for the jurisdiction
     */
    public String getDisplayName() {
        if (regionCode != null && !regionCode.trim().isEmpty()) {
            return String.format("%s (%s-%s)", name, countryCode, regionCode);
        }
        return String.format("%s (%s)", name, countryCode);
    }

    /**
     * Gets a compliance requirement by key
     */
    public String getComplianceRequirement(String key) {
        return complianceRequirements != null ? complianceRequirements.get(key) : null;
    }

    /**
     * Checks if a specific filing frequency is required
     */
    public boolean requiresFilingFrequency(String frequency) {
        return filingFrequencies != null && filingFrequencies.contains(frequency);
    }

    /**
     * Validates the jurisdiction configuration
     */
    public void validate() {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Jurisdiction code is required");
        }
        
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Jurisdiction name is required");
        }
        
        if (countryCode == null || countryCode.length() != 2) {
            throw new IllegalArgumentException("Valid country code is required");
        }
        
        if (defaultCurrency == null || defaultCurrency.length() != 3) {
            throw new IllegalArgumentException("Valid currency code is required");
        }
        
        if (maxTaxRate != null && maxTaxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maximum tax rate cannot be negative");
        }
        
        if (maxTaxAmount != null && maxTaxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maximum tax amount cannot be negative");
        }
        
        if (minTaxableAmount != null && minTaxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum taxable amount cannot be negative");
        }
    }
}