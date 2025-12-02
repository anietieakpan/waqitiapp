package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Contact Entity
 *
 * Represents customer contact information including email addresses,
 * phone numbers, and verification status.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_contact", indexes = {
    @Index(name = "idx_customer_contact_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_contact_email", columnList = "email"),
    @Index(name = "idx_customer_contact_phone", columnList = "phone_number"),
    @Index(name = "idx_customer_contact_primary", columnList = "is_primary")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "contactId")
public class CustomerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "contact_id", unique = true, nullable = false, length = 100)
    private String contactId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "extension", length = 10)
    private String extension;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_method", length = 50)
    private String verificationMethod;

    @Column(name = "is_marketing_enabled")
    @Builder.Default
    private Boolean isMarketingEnabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum ContactType {
        EMAIL,
        PHONE,
        MOBILE,
        HOME,
        WORK,
        FAX,
        OTHER
    }

    /**
     * Check if contact is primary
     *
     * @return true if primary contact
     */
    public boolean isPrimary() {
        return isPrimary != null && isPrimary;
    }

    /**
     * Check if contact is verified
     *
     * @return true if verified
     */
    public boolean isVerified() {
        return isVerified != null && isVerified;
    }

    /**
     * Check if marketing is enabled
     *
     * @return true if marketing enabled
     */
    public boolean isMarketingEnabled() {
        return isMarketingEnabled != null && isMarketingEnabled;
    }

    /**
     * Verify the contact
     *
     * @param method the verification method used
     */
    public void verify(String method) {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        this.verificationMethod = method;
    }

    /**
     * Set as primary contact
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
     * Enable marketing communications
     */
    public void enableMarketing() {
        this.isMarketingEnabled = true;
    }

    /**
     * Disable marketing communications
     */
    public void disableMarketing() {
        this.isMarketingEnabled = false;
    }

    /**
     * Get formatted phone number with country code
     *
     * @return formatted phone number
     */
    public String getFormattedPhoneNumber() {
        if (phoneNumber == null) {
            return null;
        }
        StringBuilder formatted = new StringBuilder();
        if (countryCode != null) {
            formatted.append("+").append(countryCode).append(" ");
        }
        formatted.append(phoneNumber);
        if (extension != null && !extension.isEmpty()) {
            formatted.append(" ext. ").append(extension);
        }
        return formatted.toString();
    }
}
