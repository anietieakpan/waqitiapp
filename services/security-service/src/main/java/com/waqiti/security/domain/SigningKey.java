package com.waqiti.security.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "signing_keys", indexes = {
    @Index(name = "idx_signing_key_user", columnList = "user_id"),
    @Index(name = "idx_signing_key_key_id", columnList = "key_id", unique = true),
    @Index(name = "idx_signing_key_active", columnList = "is_active"),
    @Index(name = "idx_signing_key_hardware", columnList = "hardware_device_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SigningKey {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "private_key_encrypted", columnDefinition = "TEXT")
    private String privateKeyEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "signing_method", nullable = false)
    private SigningMethod signingMethod;

    @Column(name = "algorithm", nullable = false)
    private String algorithm;

    @Column(name = "key_size")
    private Integer keySize;

    // Hardware key specific fields
    @Column(name = "hardware_device_id")
    private String hardwareDeviceId;

    @Column(name = "hardware_device_type")
    private String hardwareDeviceType;

    @Column(name = "certificate_chain", columnDefinition = "TEXT")
    private String certificateChain;

    @Column(name = "attestation_data", columnDefinition = "TEXT")
    private String attestationData;

    // Biometric key fields
    @Column(name = "biometric_template_id")
    private String biometricTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "biometric_type")
    private BiometricType biometricType;

    // Key metadata
    @Column(name = "key_name")
    private String keyName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "is_default")
    private boolean isDefault;

    // Security settings
    @Column(name = "requires_pin")
    private boolean requiresPin;

    @Column(name = "requires_biometric")
    private boolean requiresBiometric;

    @Column(name = "requires_presence")
    private boolean requiresPresence;

    // Usage restrictions
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "use_count")
    private int useCount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    // Transaction limits
    // CRITICAL FIX: Changed from Double to BigDecimal to prevent precision bypass attacks
    // Attackers could exploit floating-point precision (10000.0 vs 10000.000000001)
    @Column(name = "max_transaction_amount", precision = 19, scale = 4)
    private BigDecimal maxTransactionAmount;

    @Column(name = "daily_transaction_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "daily_transaction_total", precision = 19, scale = 4)
    private BigDecimal dailyTransactionTotal;

    @Column(name = "last_limit_reset")
    private LocalDateTime lastLimitReset;

    // Audit fields
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "created_by")
    private String createdBy;

    // Device information
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_os")
    private String deviceOs;

    @Column(name = "device_ip")
    private String deviceIp;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        useCount = 0;
        
        if (validFrom == null) {
            validFrom = LocalDateTime.now();
        }
        
        if (dailyTransactionTotal == null) {
            dailyTransactionTotal = 0.0;
        }
        
        lastLimitReset = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if key is currently valid
     */
    public boolean isValid() {
        if (!isActive || revokedAt != null) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        
        if (maxUses != null && useCount >= maxUses) {
            return false;
        }
        
        return true;
    }

    /**
     * Check if key can be used for transaction amount
     * CRITICAL FIX: Changed to BigDecimal for secure amount comparison
     */
    public boolean canSignTransaction(BigDecimal amount) {
        if (!isValid()) {
            return false;
        }

        // Check transaction amount limit
        if (maxTransactionAmount != null && amount.compareTo(maxTransactionAmount) > 0) {
            return false;
        }

        // Check daily limit
        if (dailyTransactionLimit != null) {
            // Reset daily counter if needed
            if (shouldResetDailyLimit()) {
                resetDailyLimit();
            }

            if (dailyTransactionTotal.add(amount).compareTo(dailyTransactionLimit) > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Record key usage
     * CRITICAL FIX: Changed to BigDecimal for secure amount tracking
     */
    public void recordUsage(BigDecimal transactionAmount) {
        useCount++;
        lastUsedAt = LocalDateTime.now();

        if (dailyTransactionLimit != null) {
            if (shouldResetDailyLimit()) {
                resetDailyLimit();
            }
            dailyTransactionTotal = dailyTransactionTotal.add(transactionAmount);
        }
    }

    /**
     * Revoke the key
     */
    public void revoke(String reason) {
        isActive = false;
        revokedAt = LocalDateTime.now();
        revocationReason = reason;
    }

    /**
     * Check if daily limit should be reset
     */
    private boolean shouldResetDailyLimit() {
        return lastLimitReset == null || 
               lastLimitReset.toLocalDate().isBefore(LocalDateTime.now().toLocalDate());
    }

    /**
     * Reset daily transaction limit
     */
    private void resetDailyLimit() {
        dailyTransactionTotal = BigDecimal.ZERO;
        lastLimitReset = LocalDateTime.now();
    }

    /**
     * Check if this is a hardware key
     */
    public boolean isHardwareKey() {
        return signingMethod == SigningMethod.HARDWARE_KEY && 
               hardwareDeviceId != null;
    }

    /**
     * Check if this is a biometric key
     */
    public boolean isBiometricKey() {
        return signingMethod == SigningMethod.BIOMETRIC && 
               biometricTemplateId != null;
    }

    /**
     * Get key display name
     */
    public String getDisplayName() {
        if (keyName != null && !keyName.isEmpty()) {
            return keyName;
        }
        
        switch (signingMethod) {
            case HARDWARE_KEY:
                return hardwareDeviceType + " Hardware Key";
            case BIOMETRIC:
                return biometricType + " Biometric Key";
            case SOFTWARE_KEY:
                return "Software Key " + keyId.substring(0, 8);
            default:
                return "Signing Key " + keyId.substring(0, 8);
        }
    }
}