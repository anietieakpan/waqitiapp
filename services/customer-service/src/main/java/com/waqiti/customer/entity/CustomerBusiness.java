package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Business Entity
 *
 * Represents business customer profile data including company information,
 * industry classification, and financial details.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_business", indexes = {
    @Index(name = "idx_customer_business_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_business_name", columnList = "business_name"),
    @Index(name = "idx_customer_business_industry", columnList = "industry")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "id")
public class CustomerBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", unique = true, nullable = false, length = 100)
    private String customerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    @Column(name = "industry", nullable = false, length = 100)
    private String industry;

    @Column(name = "industry_code", length = 20)
    private String industryCode;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "tax_id_encrypted", nullable = false, length = 255)
    private String taxIdEncrypted;

    @Column(name = "tax_id_hash", nullable = false, length = 128)
    private String taxIdHash;

    @Column(name = "date_of_incorporation")
    private LocalDate dateOfIncorporation;

    @Column(name = "country_of_incorporation", length = 3)
    private String countryOfIncorporation;

    @Column(name = "number_of_employees")
    private Integer numberOfEmployees;

    @Column(name = "annual_revenue", precision = 18, scale = 2)
    private BigDecimal annualRevenue;

    @Column(name = "revenue_currency", length = 3)
    @Builder.Default
    private String revenueCurrency = "USD";

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "is_publicly_traded")
    @Builder.Default
    private Boolean isPubliclyTraded = false;

    @Column(name = "stock_symbol", length = 20)
    private String stockSymbol;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if business is publicly traded
     *
     * @return true if publicly traded
     */
    public boolean isPubliclyTraded() {
        return isPubliclyTraded != null && isPubliclyTraded;
    }

    /**
     * Check if business has stock symbol
     *
     * @return true if stock symbol is present
     */
    public boolean hasStockSymbol() {
        return stockSymbol != null && !stockSymbol.isEmpty();
    }

    /**
     * Check if business has website
     *
     * @return true if website URL is present
     */
    public boolean hasWebsite() {
        return websiteUrl != null && !websiteUrl.isEmpty();
    }

    /**
     * Get business age in years
     *
     * @return age in years, or null if date of incorporation is not set
     */
    public Integer getBusinessAgeYears() {
        if (dateOfIncorporation == null) {
            return null;
        }
        return LocalDate.now().getYear() - dateOfIncorporation.getYear();
    }

    /**
     * Check if business is small (less than 50 employees)
     *
     * @return true if small business
     */
    public boolean isSmallBusiness() {
        return numberOfEmployees != null && numberOfEmployees < 50;
    }

    /**
     * Check if business is medium (50-249 employees)
     *
     * @return true if medium business
     */
    public boolean isMediumBusiness() {
        return numberOfEmployees != null && numberOfEmployees >= 50 && numberOfEmployees < 250;
    }

    /**
     * Check if business is large (250+ employees)
     *
     * @return true if large business
     */
    public boolean isLargeBusiness() {
        return numberOfEmployees != null && numberOfEmployees >= 250;
    }
}
