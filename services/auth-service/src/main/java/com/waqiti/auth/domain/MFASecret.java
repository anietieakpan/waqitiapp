package com.waqiti.auth.domain;

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
 * MFA Secret Entity - Stores encrypted TOTP secrets per user
 *
 * SECURITY REQUIREMENTS:
 * - Secrets are encrypted at rest using AES-256-GCM
 * - Secrets are unique per user
 * - Secrets are rotatable
 * - Secrets have expiry dates
 * - Failed verification attempts are tracked
 *
 * COMPLIANCE:
 * - PCI-DSS 8.3: Multi-factor authentication
 * - SOC2 CC6.1: Logical and physical access controls
 * - NIST 800-63B: Authenticator lifecycle management
 */
@Entity
@Table(name = "mfa_secrets", indexes = {
    @Index(name = "idx_mfa_user_id", columnList = "user_id"),
    @Index(name = "idx_mfa_enabled", columnList = "enabled"),
    @Index(name = "idx_mfa_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MFASecret {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * Encrypted TOTP secret (Base32 encoded)
     * Encryption: AES-256-GCM with unique IV per secret
     * Format: IV:EncryptedSecret:AuthTag (hex-encoded)
     */
    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedSecret;

    /**
     * Encryption key version for rotation support
     */
    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    /**
     * MFA method type (TOTP, SMS, EMAIL, HARDWARE_KEY)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_method", nullable = false)
    private MFAMethod mfaMethod;

    /**
     * Whether MFA is enabled for this user
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /**
     * Backup codes (encrypted, comma-separated)
     * User gets 10 single-use backup codes
     */
    @Column(name = "encrypted_backup_codes", columnDefinition = "TEXT")
    private String encryptedBackupCodes;

    /**
     * Number of backup codes remaining
     */
    @Column(name = "backup_codes_remaining")
    @Builder.Default
    private Integer backupCodesRemaining = 10;

    /**
     * Failed verification attempts counter
     * Auto-disables MFA after 5 consecutive failures
     */
    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0;

    /**
     * Last successful verification timestamp
     */
    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    /**
     * Secret expiry date (for rotation)
     * Secrets expire after 1 year
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Device identifier for device-specific MFA
     */
    @Column(name = "device_id")
    private String deviceId;

    /**
     * Trusted device flag (skip MFA for 30 days on this device)
     */
    @Column(name = "trusted_device")
    @Builder.Default
    private Boolean trustedDevice = false;

    /**
     * Trusted device expiry
     */
    @Column(name = "trusted_until")
    private LocalDateTime trustedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version")
    private Long version;

    // Business logic methods

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isTrusted() {
        return trustedDevice && trustedUntil != null && LocalDateTime.now().isBefore(trustedUntil);
    }

    public boolean shouldLockout() {
        return failedAttempts >= 5;
    }

    public void recordFailedAttempt() {
        this.failedAttempts++;
        if (shouldLockout()) {
            this.enabled = false;
        }
    }

    public void recordSuccessfulVerification() {
        this.failedAttempts = 0;
        this.lastVerifiedAt = LocalDateTime.now();
    }

    public void useBackupCode() {
        if (backupCodesRemaining > 0) {
            this.backupCodesRemaining--;
        }
    }

    public boolean needsRotation() {
        // Rotate if expired or created more than 1 year ago
        return isExpired() ||
               (createdAt != null && createdAt.plusYears(1).isBefore(LocalDateTime.now()));
    }

    public enum MFAMethod {
        TOTP,           // Time-based One-Time Password (Google Authenticator, Authy)
        SMS,            // SMS verification code
        EMAIL,          // Email verification code
        HARDWARE_KEY,   // FIDO2/WebAuthn hardware key
        BACKUP_CODE     // Single-use backup code
    }
}
