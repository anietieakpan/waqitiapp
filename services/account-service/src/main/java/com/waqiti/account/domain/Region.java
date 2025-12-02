package com.waqiti.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing geographical regions for account number generation
 */
@Entity
@Table(name = "regions", indexes = {
        @Index(name = "idx_region_code", columnList = "code", unique = true),
        @Index(name = "idx_country_code", columnList = "country_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Region {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", unique = true, nullable = false, length = 10)
    private String code; // e.g., "US-CA", "UK", "AU-NSW"
    
    @Column(name = "name", nullable = false, length = 100)
    private String name; // e.g., "California", "United Kingdom", "New South Wales"
    
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode; // ISO 3166-1 alpha-2/3 codes
    
    @Column(name = "country_name", nullable = false, length = 100)
    private String countryName;
    
    @Column(name = "timezone", length = 50)
    private String timezone; // e.g., "America/Los_Angeles"
    
    @Column(name = "currency_code", length = 3)
    private String currencyCode; // e.g., "USD", "EUR"
    
    @Column(name = "regulatory_code", length = 20)
    private String regulatoryCode; // Banking regulatory identifier
    
    @Column(name = "active")
    private boolean active = true;
    
    @Column(name = "supports_international_transfers")
    private boolean supportsInternationalTransfers = true;
    
    @Column(name = "requires_kyc")
    private boolean requiresKyc = true;
    
    @Column(name = "max_transaction_limit")
    private Long maxTransactionLimit; // In base currency units
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Convenience methods
    
    public boolean isEuropeanUnion() {
        return countryCode.matches("^(AT|BE|BG|HR|CY|CZ|DK|EE|FI|FR|DE|GR|HU|IE|IT|LV|LT|LU|MT|NL|PL|PT|RO|SK|SI|ES|SE)$");
    }
    
    public boolean isNorthAmerica() {
        return countryCode.matches("^(US|CA|MX)$");
    }
    
    public boolean requiresSpecialCompliance() {
        // Regions with strict financial regulations
        return countryCode.matches("^(US|CH|SG|JP|AU)$");
    }
}