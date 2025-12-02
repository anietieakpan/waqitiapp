package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Identification Entity
 *
 * Represents customer identification documents including passports,
 * driver's licenses, national IDs, and other government-issued documents.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_identification", indexes = {
    @Index(name = "idx_customer_identification_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_identification_type", columnList = "id_type"),
    @Index(name = "idx_customer_identification_expiry", columnList = "expiry_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "identificationId")
public class CustomerIdentification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "identification_id", unique = true, nullable = false, length = 100)
    private String identificationId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 50)
    private IdType idType;

    @Column(name = "id_number_encrypted", nullable = false, length = 255)
    private String idNumberEncrypted;

    @Column(name = "id_number_hash", nullable = false, length = 128)
    private String idNumberHash;

    @Column(name = "issuing_country", nullable = false, length = 3)
    private String issuingCountry;

    @Column(name = "issuing_authority", length = 255)
    private String issuingAuthority;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "is_expired")
    @Builder.Default
    private Boolean isExpired = false;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "document_url", columnDefinition = "TEXT")
    private String documentUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum IdType {
        PASSPORT,
        DRIVERS_LICENSE,
        NATIONAL_ID,
        STATE_ID,
        MILITARY_ID,
        VOTER_ID,
        RESIDENCE_PERMIT,
        WORK_PERMIT,
        BIRTH_CERTIFICATE,
        SSN_CARD,
        TAX_ID_CARD,
        OTHER
    }

    /**
     * Check if identification is expired
     *
     * @return true if expired
     */
    public boolean isExpired() {
        if (isExpired != null && isExpired) {
            return true;
        }
        if (expiryDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Check if identification is verified
     *
     * @return true if verified
     */
    public boolean isVerified() {
        return isVerified != null && isVerified;
    }

    /**
     * Check if identification is expiring soon (within 90 days)
     *
     * @return true if expiring soon
     */
    public boolean isExpiringSoon() {
        if (expiryDate == null || isExpired()) {
            return false;
        }
        LocalDate now = LocalDate.now();
        LocalDate threshold = now.plusDays(90);
        return expiryDate.isBefore(threshold) || expiryDate.isEqual(threshold);
    }

    /**
     * Get days until expiry
     *
     * @return days until expiry, negative if expired, null if no expiry date
     */
    public Long getDaysUntilExpiry() {
        if (expiryDate == null) {
            return null;
        }
        LocalDate now = LocalDate.now();
        return java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);
    }

    /**
     * Check if identification is currently valid
     *
     * @return true if valid (not expired and verified)
     */
    public boolean isValid() {
        return isVerified() && !isExpired();
    }

    /**
     * Verify the identification
     *
     * @param verifiedBy the user or system that verified the identification
     */
    public void verify(String verifiedBy) {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        this.verifiedBy = verifiedBy;
    }

    /**
     * Mark as expired
     */
    public void markAsExpired() {
        this.isExpired = true;
    }

    /**
     * Check expiry and update status if needed
     */
    public void updateExpiryStatus() {
        if (expiryDate != null && LocalDate.now().isAfter(expiryDate)) {
            this.isExpired = true;
        }
    }
}
