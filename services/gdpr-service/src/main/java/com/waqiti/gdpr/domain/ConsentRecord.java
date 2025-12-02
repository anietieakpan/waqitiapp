package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "purpose", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConsentPurpose purpose;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConsentStatus status;
    
    @Column(name = "version", nullable = false)
    private String consentVersion;
    
    @Column(name = "granted_at")
    private LocalDateTime grantedAt;
    
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "collection_method")
    @Enumerated(EnumType.STRING)
    private CollectionMethod collectionMethod;
    
    @Column(name = "consent_text", columnDefinition = "TEXT")
    private String consentText;
    
    @Column(name = "lawful_basis")
    @Enumerated(EnumType.STRING)
    private LawfulBasis lawfulBasis;
    
    @Column(name = "third_parties", length = 1000)
    private String thirdParties;
    
    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;
    
    @Column(name = "is_minor")
    private Boolean isMinor;
    
    @Column(name = "parental_consent_id")
    private String parentalConsentId;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (status == ConsentStatus.GRANTED && grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
    }
    
    public boolean isActive() {
        return status == ConsentStatus.GRANTED &&
               (expiresAt == null || LocalDateTime.now().isBefore(expiresAt));
    }
    
    public void withdraw() {
        this.status = ConsentStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }
}

enum ConsentPurpose {
    ESSENTIAL_SERVICE,       // Core functionality
    MARKETING_EMAILS,        // Marketing communications
    PROMOTIONAL_SMS,         // SMS marketing
    PUSH_NOTIFICATIONS,      // Mobile push notifications
    ANALYTICS,              // Usage analytics
    PERSONALIZATION,        // Personalized experience
    THIRD_PARTY_SHARING,    // Sharing with partners
    PROFILING,              // User profiling
    AUTOMATED_DECISIONS,    // Automated decision making
    LOCATION_TRACKING,      // Location services
    BIOMETRIC_DATA,         // Biometric authentication
    CROSS_BORDER_TRANSFER   // International data transfer
}

enum ConsentStatus {
    GRANTED,
    WITHDRAWN,
    EXPIRED,
    PENDING
}

enum CollectionMethod {
    EXPLICIT_CHECKBOX,      // User checked a box
    IMPLICIT_SIGNUP,        // Part of signup process
    EMAIL_CONFIRMATION,     // Confirmed via email
    IN_APP_PROMPT,         // In-app consent dialog
    SETTINGS_PAGE,         // Changed in settings
    CUSTOMER_SUPPORT,      // Via support interaction
    IMPORTED              // Migrated from old system
}

enum LawfulBasis {
    CONSENT,                // User consent
    CONTRACT,               // Necessary for contract
    LEGAL_OBLIGATION,       // Legal requirement
    VITAL_INTERESTS,        // Protect vital interests
    PUBLIC_TASK,           // Public interest task
    LEGITIMATE_INTERESTS    // Legitimate business interests
}