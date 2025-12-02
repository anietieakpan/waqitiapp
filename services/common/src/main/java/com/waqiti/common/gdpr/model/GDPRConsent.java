package com.waqiti.common.gdpr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Consent Entity (referenced by GDPRConsentRepository)
 */
@Entity
@Table(name = "gdpr_consents", indexes = {
        @Index(name = "idx_consent_user_id", columnList = "user_id"),
        @Index(name = "idx_consent_type", columnList = "consent_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "consent_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConsentRecord.ConsentType consentType;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "consent_date", nullable = false)
    private LocalDateTime consentDate;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}