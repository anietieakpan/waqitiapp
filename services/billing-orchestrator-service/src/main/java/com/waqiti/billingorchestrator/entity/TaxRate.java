package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tax Rate Entity
 *
 * Stores tax rates for different jurisdictions and tax types.
 *
 * JURISDICTIONS SUPPORTED:
 * - US States (sales tax)
 * - US Counties/Cities (local tax)
 * - Canada Provinces (GST/PST/HST)
 * - EU Countries (VAT)
 * - Custom jurisdictions
 *
 * TAX TYPES:
 * - Sales Tax (US)
 * - VAT (EU)
 * - GST/PST/HST (Canada)
 * - Service Tax
 * - Digital Goods Tax
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Entity
@Table(name = "tax_rates", indexes = {
    @Index(name = "idx_tax_jurisdiction", columnList = "jurisdiction"),
    @Index(name = "idx_tax_type", columnList = "tax_type"),
    @Index(name = "idx_tax_effective", columnList = "effective_date DESC"),
    @Index(name = "idx_tax_active", columnList = "active, jurisdiction")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaxRate extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    // Jurisdiction
    @Column(name = "jurisdiction", nullable = false, length = 100)
    private String jurisdiction;  // e.g., "US-CA-94102", "GB", "CA-ON"

    @Column(name = "country_code", length = 2)
    private String countryCode;  // ISO 3166-1 alpha-2

    @Column(name = "state_province", length = 50)
    private String stateProvince;  // State/Province code

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    // Tax details
    @Column(name = "tax_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TaxType taxType;

    @Column(name = "tax_name", length = 100)
    private String taxName;  // e.g., "California Sales Tax", "UK VAT"

    @Column(name = "rate", precision = 10, scale = 6, nullable = false)
    private BigDecimal rate;  // e.g., 0.0875 for 8.75%

    // Effective dates
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "active")
    private Boolean active;

    // Tax authority
    @Column(name = "tax_authority", length = 200)
    private String taxAuthority;  // e.g., "California Department of Tax and Fee Administration"

    // Product/service applicability
    @Column(name = "applies_to_goods")
    private Boolean appliesToGoods;

    @Column(name = "applies_to_services")
    private Boolean appliesToServices;

    @Column(name = "applies_to_digital")
    private Boolean appliesToDigital;

    // External reference
    @Column(name = "external_id", length = 100)
    private String externalId;  // Reference to external tax service (Avalara, TaxJar)

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    private Long version;

    public enum TaxType {
        SALES_TAX,          // US sales tax
        USE_TAX,            // US use tax
        VAT,                // Value Added Tax (EU, UK)
        GST,                // Goods and Services Tax (Canada, Australia)
        PST,                // Provincial Sales Tax (Canada)
        HST,                // Harmonized Sales Tax (Canada)
        SERVICE_TAX,        // Service-specific tax
        DIGITAL_SERVICES_TAX, // Digital goods tax
        EXCISE_TAX,         // Excise tax
        CUSTOM_TAX          // Custom tax type
    }

    /**
     * Checks if tax rate is currently active
     */
    public boolean isCurrentlyActive() {
        LocalDate now = LocalDate.now();
        return Boolean.TRUE.equals(active) &&
               !now.isBefore(effectiveDate) &&
               (expiryDate == null || !now.isAfter(expiryDate));
    }

    /**
     * Calculates tax amount for given subtotal
     */
    public BigDecimal calculateTax(BigDecimal subtotal) {
        return subtotal.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
