package com.waqiti.common.gdpr.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Consent Record Entity
 *
 * Tracks user consent for data processing activities as required by GDPR Article 6 and 7.
 * Maintains complete audit trail of consent changes for regulatory compliance.
 *
 * Compliance:
 * - GDPR Article 6: Lawfulness of processing
 * - GDPR Article 7: Conditions for consent
 * - GDPR Article 13: Information to be provided
 */
@Entity
@Table(name = "gdpr_consent_records", indexes = {
    @Index(name = "idx_consent_user_id", columnList = "user_id"),
    @Index(name = "idx_consent_type", columnList = "consent_type"),
    @Index(name = "idx_consent_granted_at", columnList = "granted_at"),
    @Index(name = "idx_consent_active", columnList = "user_id, consent_type, is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "consent_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private ConsentType consentType;

    @NotNull
    @Column(name = "is_granted", nullable = false)
    private Boolean isGranted;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "legal_basis", length = 100)
    private String legalBasis; // CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTEREST, PUBLIC_TASK, LEGITIMATE_INTEREST

    @Column(name = "consent_method", length = 50)
    private String consentMethod; // WEB_FORM, MOBILE_APP, API, EMAIL, PHONE

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "geolocation", length = 100)
    private String geolocation;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "version", nullable = false)
    private Integer version = 1; // Consent version for tracking policy changes

    @Column(name = "parent_consent_id")
    private UUID parentConsentId; // Links to previous consent record

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata; // Additional context

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Consent Types as per GDPR requirements
     */
    public enum ConsentType {
        ESSENTIAL,              // Essential cookies and processing
        ANALYTICS,              // Analytics and performance tracking
        MARKETING,              // Marketing communications
        PERSONALIZATION,        // Personalized content and recommendations
        THIRD_PARTY_SHARING,    // Sharing data with third parties
        PROFILING,              // Automated profiling and decision making
        LOCATION_TRACKING,      // Location data processing
        BIOMETRIC_DATA,         // Biometric data processing
        FINANCIAL_DATA,         // Financial data processing
        COMMUNICATIONS,         // Email, SMS, Push notifications
        DATA_RETENTION,         // Extended data retention beyond legal requirement
        CROSS_BORDER_TRANSFER   // Data transfer outside EU/EEA
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isGranted && grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (!isGranted && revokedAt == null) {
            revokedAt = LocalDateTime.now();
        }
    }

    /**
     * Check if consent is currently valid
     */
    public boolean isValid() {
        if (!isActive || !isGranted) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Revoke this consent
     */
    public void revoke(String revokedBy) {
        this.isGranted = false;
        this.isActive = false;
        this.revokedAt = LocalDateTime.now();
        this.updatedBy = revokedBy;
    }
}
