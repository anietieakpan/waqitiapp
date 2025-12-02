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
 * Customer Address Entity
 *
 * Represents customer address information including physical addresses,
 * verification status, and geographical coordinates.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_address", indexes = {
    @Index(name = "idx_customer_address_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_address_country", columnList = "country_code"),
    @Index(name = "idx_customer_address_primary", columnList = "is_primary")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "addressId")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "address_id", unique = true, nullable = false, length = 100)
    private String addressId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "address_line3", length = 255)
    private String addressLine3;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private LocalDate validFrom = LocalDate.now();

    @Column(name = "valid_to")
    private LocalDate validTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum AddressType {
        HOME,
        WORK,
        MAILING,
        BILLING,
        SHIPPING,
        BUSINESS,
        OTHER
    }

    /**
     * Check if address is primary
     *
     * @return true if primary address
     */
    public boolean isPrimary() {
        return isPrimary != null && isPrimary;
    }

    /**
     * Check if address is verified
     *
     * @return true if verified
     */
    public boolean isVerified() {
        return isVerified != null && isVerified;
    }

    /**
     * Check if address is currently valid
     *
     * @return true if valid
     */
    public boolean isValid() {
        LocalDate now = LocalDate.now();
        boolean afterValidFrom = validFrom == null || !now.isBefore(validFrom);
        boolean beforeValidTo = validTo == null || !now.isAfter(validTo);
        return afterValidFrom && beforeValidTo;
    }

    /**
     * Check if address has expired
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return validTo != null && LocalDate.now().isAfter(validTo);
    }

    /**
     * Check if address has coordinates
     *
     * @return true if latitude and longitude are set
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * Verify the address
     *
     * @param verifiedBy the user or system that verified the address
     */
    public void verify(String verifiedBy) {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        this.verifiedBy = verifiedBy;
    }

    /**
     * Set as primary address
     */
    public void setAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Remove primary status
     */
    public void removeFromPrimary() {
        this.isPrimary = false;
    }

    /**
     * Set coordinates
     *
     * @param latitude the latitude
     * @param longitude the longitude
     */
    public void setCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Get full address as a single string
     *
     * @return formatted full address
     */
    public String getFullAddress() {
        StringBuilder address = new StringBuilder(addressLine1);
        if (addressLine2 != null && !addressLine2.isEmpty()) {
            address.append(", ").append(addressLine2);
        }
        if (addressLine3 != null && !addressLine3.isEmpty()) {
            address.append(", ").append(addressLine3);
        }
        address.append(", ").append(city);
        if (state != null && !state.isEmpty()) {
            address.append(", ").append(state);
        }
        address.append(" ").append(postalCode);
        address.append(", ").append(countryCode);
        return address.toString();
    }
}
