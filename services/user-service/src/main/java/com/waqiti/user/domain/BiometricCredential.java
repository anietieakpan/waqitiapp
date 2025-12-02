package com.waqiti.user.domain;

import com.waqiti.common.audit.AuditableEntity;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive Biometric Credential Entity
 * Stores encrypted biometric templates and metadata with advanced security features
 */
@Entity
@Table(name = "biometric_credentials", indexes = {
    @Index(name = "idx_bio_user_type", columnList = "user_id, biometric_type"),
    @Index(name = "idx_bio_credential_id", columnList = "credential_id"),
    @Index(name = "idx_bio_device", columnList = "device_fingerprint"),
    @Index(name = "idx_bio_status", columnList = "status"),
    @Index(name = "idx_bio_created", columnList = "created_at"),
    @Index(name = "idx_bio_last_used", columnList = "last_used_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BiometricCredential extends AuditableEntity {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "credential_id", unique = true, nullable = false, length = 128)
    private String credentialId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "biometric_type", nullable = false, length = 50)
    private BiometricType biometricType;
    
    @Column(name = "device_fingerprint", nullable = false, length = 255)
    private String deviceFingerprint;
    
    @Column(name = "device_name", length = 100)
    private String deviceName;
    
    @Lob
    @Column(name = "encrypted_template", nullable = false)
    private String encryptedTemplate;
    
    @Column(name = "template_version", length = 20)
    private String templateVersion;
    
    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm;
    
    @Column(name = "quality_score")
    private Double qualityScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;
    
    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;
    
    @Column(name = "max_usage_count")
    private Long maxUsageCount;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "registration_ip", length = 45)
    private String registrationIp;
    
    @Column(name = "registration_user_agent", length = 500)
    private String registrationUserAgent;
    
    @Column(name = "trust_score")
    private Double trustScore;
    
    @Column(name = "risk_score")
    private Double riskScore;
    
    @Column(name = "continuous_auth_enabled")
    @Builder.Default
    private Boolean continuousAuthEnabled = false;
    
    @Column(name = "challenge_response_required")
    @Builder.Default
    private Boolean challengeResponseRequired = true;
    
    @Column(name = "multi_factor_required")
    @Builder.Default
    private Boolean multiFactorRequired = false;
    
    @ElementCollection
    @CollectionTable(
        name = "biometric_credential_metadata",
        joinColumns = @JoinColumn(name = "credential_id")
    )
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, Object> registrationMetadata;
    
    @ElementCollection
    @CollectionTable(
        name = "biometric_usage_history",
        joinColumns = @JoinColumn(name = "credential_id")
    )
    @MapKeyColumn(name = "usage_date")
    @Column(name = "usage_details", length = 2000)
    private Map<String, String> usageHistory;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Biometric Credential Status
     */
    public enum Status {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        REVOKED,
        SUSPENDED,
        PENDING_VERIFICATION,
        COMPROMISED
    }
    
    /**
     * Lifecycle methods
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastUsedAt == null) {
            lastUsedAt = createdAt;
        }
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Business methods
     */
    public void incrementUsageCount() {
        this.usageCount++;
        this.lastUsedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return Status.ACTIVE.equals(this.status) && 
               (this.expiresAt == null || this.expiresAt.isAfter(LocalDateTime.now()));
    }
    
    public boolean isExpired() {
        return Status.EXPIRED.equals(this.status) || 
               (this.expiresAt != null && this.expiresAt.isBefore(LocalDateTime.now()));
    }
    
    public boolean isRevoked() {
        return Status.REVOKED.equals(this.status);
    }
    
    public boolean isSuspended() {
        return Status.SUSPENDED.equals(this.status);
    }
    
    public boolean isCompromised() {
        return Status.COMPROMISED.equals(this.status);
    }
    
    public boolean canBeUsed() {
        return isActive() && !isExpired() && !isRevoked() && !isSuspended() && !isCompromised();
    }
    
    public boolean hasReachedUsageLimit() {
        return maxUsageCount != null && usageCount >= maxUsageCount;
    }
    
    public void revoke(String reason) {
        this.status = Status.REVOKED;
        this.revokedAt = LocalDateTime.now();
        this.revocationReason = reason;
    }
    
    public void suspend(String reason) {
        this.status = Status.SUSPENDED;
        this.revocationReason = reason;
    }
    
    public void markCompromised(String reason) {
        this.status = Status.COMPROMISED;
        this.revocationReason = reason;
    }
    
    public void activate() {
        this.status = Status.ACTIVE;
        this.revocationReason = null;
    }
    
    public void updateTrustScore(Double newTrustScore) {
        this.trustScore = newTrustScore;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void updateRiskScore(Double newRiskScore) {
        this.riskScore = newRiskScore;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void addUsageRecord(String details) {
        if (this.usageHistory == null) {
            this.usageHistory = new java.util.HashMap<>();
        }
        this.usageHistory.put(LocalDateTime.now().toString(), details);
        
        // Keep only last 50 usage records
        if (this.usageHistory.size() > 50) {
            String oldestKey = this.usageHistory.keySet().stream()
                .min(String::compareTo)
                .orElse(null);
            if (oldestKey != null) {
                this.usageHistory.remove(oldestKey);
            }
        }
    }
    
    public boolean requiresChallenge() {
        return Boolean.TRUE.equals(this.challengeResponseRequired);
    }
    
    public boolean supportsMultiFactor() {
        return Boolean.TRUE.equals(this.multiFactorRequired);
    }
    
    public boolean supportsContinuousAuth() {
        return Boolean.TRUE.equals(this.continuousAuthEnabled);
    }
    
    /**
     * Security validation methods
     */
    public boolean isHighRisk() {
        return this.riskScore != null && this.riskScore > 0.7;
    }
    
    public boolean isLowTrust() {
        return this.trustScore != null && this.trustScore < 0.3;
    }
    
    public boolean requiresSecurityReview() {
        return isHighRisk() || isLowTrust() || 
               (this.usageCount > 1000 && this.lastUsedAt.isBefore(LocalDateTime.now().minusDays(90)));
    }
    
    public String getSecurityLevel() {
        if (isHighRisk()) return "HIGH_RISK";
        if (isLowTrust()) return "LOW_TRUST";
        if (this.trustScore != null && this.trustScore > 0.8) return "HIGH_TRUST";
        return "STANDARD";
    }
    
    /**
     * Audit and compliance methods
     */
    public boolean requiresAuditTrail() {
        return this.biometricType == BiometricType.FINGERPRINT || 
               this.biometricType == BiometricType.IRIS ||
               this.riskScore != null && this.riskScore > 0.5;
    }
    
    public String getComplianceStatus() {
        if (!canBeUsed()) return "NON_COMPLIANT";
        if (requiresSecurityReview()) return "REQUIRES_REVIEW";
        if (this.qualityScore != null && this.qualityScore < 0.7) return "QUALITY_CONCERNS";
        return "COMPLIANT";
    }
}