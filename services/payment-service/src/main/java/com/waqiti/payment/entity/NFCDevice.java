package com.waqiti.payment.entity;

import com.waqiti.common.entity.BaseEntity;
import lombok.*;
import org.hibernate.envers.Audited;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Entity representing an NFC-enabled device registered for payment processing
 */
@Entity
@Table(name = "nfc_devices", indexes = {
    @Index(name = "idx_device_id", columnList = "device_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_device_fingerprint", columnList = "device_fingerprint"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_last_used_at", columnList = "last_used_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class NFCDevice extends BaseEntity {

    @Column(name = "device_id", nullable = false, unique = true, length = 255)
    private String deviceId;
    
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;
    
    @Column(name = "device_name", length = 255)
    private String deviceName;
    
    @Column(name = "device_type", length = 100)
    private String deviceType;
    
    @Column(name = "manufacturer", length = 100)
    private String manufacturer;
    
    @Column(name = "model", length = 100)
    private String model;
    
    @Column(name = "os_version", length = 50)
    private String osVersion;
    
    @Column(name = "nfc_capability", length = 100)
    private String nfcCapability;
    
    @Column(name = "device_fingerprint", unique = true, length = 255)
    private String deviceFingerprint;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NFCDeviceStatus status;
    
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "usage_count")
    @Builder.Default
    private Long usageCount = 0L;
    
    @Column(name = "transaction_count")
    @Builder.Default
    private Long transactionCount = 0L;
    
    // Security features
    @Column(name = "is_trusted")
    @Builder.Default
    private Boolean isTrusted = false;
    
    @Column(name = "trust_score")
    @Builder.Default
    private Double trustScore = 0.0;
    
    @Column(name = "trust_score_updated_at")
    private Instant trustScoreUpdatedAt;
    
    @Column(name = "is_compromised")
    @Builder.Default
    private Boolean isCompromised = false;
    
    @Column(name = "compromised_at")
    private Instant compromisedAt;
    
    @Column(name = "compromised_reason", length = 500)
    private String compromisedReason;
    
    @Column(name = "requires_reauthentication")
    @Builder.Default
    private Boolean requiresReauthentication = false;
    
    // Certificate and keys
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;
    
    @Column(name = "certificate", columnDefinition = "TEXT")
    private String certificate;
    
    @Column(name = "certificate_expiry_date")
    private Instant certificateExpiryDate;
    
    // Location tracking
    @Column(name = "last_known_latitude")
    private Double lastKnownLatitude;
    
    @Column(name = "last_known_longitude")
    private Double lastKnownLongitude;
    
    @Column(name = "last_known_location", length = 255)
    private String lastKnownLocation;
    
    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;
    
    // Device capabilities
    @Column(name = "supports_biometric")
    @Builder.Default
    private Boolean supportsBiometric = false;
    
    @Column(name = "supports_secure_element")
    @Builder.Default
    private Boolean supportsSecureElement = false;
    
    @Column(name = "supports_tokenization")
    @Builder.Default
    private Boolean supportsTokenization = false;
    
    @Column(name = "max_transaction_amount")
    private java.math.BigDecimal maxTransactionAmount;
    
    // Security and compliance
    @Column(name = "security_patch_level", length = 50)
    private String securityPatchLevel;
    
    @Column(name = "app_version", length = 50)
    private String appVersion;
    
    @Column(name = "sdk_version", length = 50)
    private String sdkVersion;
    
    @Column(name = "is_rooted")
    @Builder.Default
    private Boolean isRooted = false;
    
    @Column(name = "is_emulator")
    @Builder.Default
    private Boolean isEmulator = false;
    
    // Additional metadata
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    /**
     * Check if device is active
     */
    public boolean isActive() {
        return status == NFCDeviceStatus.ACTIVE;
    }
    
    /**
     * Check if device can process transactions
     */
    public boolean canProcessTransactions() {
        return isActive() && !isCompromised && !requiresReauthentication;
    }
    
    /**
     * Check if device certificate is expired
     */
    public boolean isCertificateExpired() {
        return certificateExpiryDate != null && certificateExpiryDate.isBefore(Instant.now());
    }
    
    /**
     * Update last used timestamp
     */
    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
        this.usageCount++;
    }
    
    /**
     * Mark device as compromised
     */
    public void markAsCompromised(String reason) {
        this.isCompromised = true;
        this.compromisedAt = Instant.now();
        this.compromisedReason = reason;
        this.status = NFCDeviceStatus.BLOCKED;
    }
}