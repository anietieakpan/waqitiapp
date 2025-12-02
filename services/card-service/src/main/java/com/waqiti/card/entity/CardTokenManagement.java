package com.waqiti.card.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardTokenManagement entity - Token management record
 * Represents tokenization of card data for secure storage and transmission
 *
 * Tokenization replaces sensitive card data (PAN, CVV) with non-sensitive tokens
 * for PCI-DSS compliance and security
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_token_management", indexes = {
    @Index(name = "idx_token_mgmt_id", columnList = "token_id"),
    @Index(name = "idx_token_mgmt_card", columnList = "card_id"),
    @Index(name = "idx_token_mgmt_pan_token", columnList = "pan_token"),
    @Index(name = "idx_token_mgmt_type", columnList = "token_type"),
    @Index(name = "idx_token_mgmt_status", columnList = "token_status"),
    @Index(name = "idx_token_mgmt_expiry", columnList = "expires_at"),
    @Index(name = "idx_token_mgmt_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardTokenManagement extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // TOKEN IDENTIFICATION
    // ========================================================================

    @Column(name = "token_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Token ID is required")
    private String tokenId;

    @Column(name = "pan_token", unique = true, nullable = false, length = 100)
    @NotBlank(message = "PAN token is required")
    private String panToken;

    @Column(name = "token_type", nullable = false, length = 50)
    @NotBlank(message = "Token type is required")
    private String tokenType;

    @Column(name = "token_version", length = 20)
    @Size(max = 20)
    private String tokenVersion;

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    // ========================================================================
    // TOKEN DETAILS
    // ========================================================================

    @Column(name = "token_status", nullable = false, length = 30)
    @NotBlank(message = "Token status is required")
    @Builder.Default
    private String tokenStatus = "ACTIVE";

    @Column(name = "token_requestor", length = 100)
    @Size(max = 100)
    private String tokenRequestor;

    @Column(name = "token_requestor_id", length = 11)
    @Size(max = 11)
    private String tokenRequestorId;

    @Column(name = "created_at_timestamp", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDateTime createdAtTimestamp = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_expired")
    @Builder.Default
    private Boolean isExpired = false;

    // ========================================================================
    // TOKENIZATION PROVIDER
    // ========================================================================

    @Column(name = "tokenization_provider", length = 50)
    @Size(max = 50)
    private String tokenizationProvider;

    @Column(name = "provider_token_id", length = 100)
    @Size(max = 100)
    private String providerTokenId;

    @Column(name = "network_token_provider", length = 50)
    @Size(max = 50)
    private String networkTokenProvider;

    @Column(name = "network_token_id", length = 100)
    @Size(max = 100)
    private String networkTokenId;

    // ========================================================================
    // TOKEN SCOPE & USAGE
    // ========================================================================

    @Column(name = "token_scope", length = 50)
    @Size(max = 50)
    private String tokenScope;

    @Column(name = "usage_type", length = 50)
    @Size(max = 50)
    private String usageType;

    @Column(name = "single_use")
    @Builder.Default
    private Boolean singleUse = false;

    @Column(name = "multi_use")
    @Builder.Default
    private Boolean multiUse = true;

    @Column(name = "max_usage_count")
    @Min(0)
    private Integer maxUsageCount;

    @Column(name = "current_usage_count")
    @Builder.Default
    private Integer currentUsageCount = 0;

    @Column(name = "last_used_date")
    private LocalDateTime lastUsedDate;

    // ========================================================================
    // DEVICE BINDING
    // ========================================================================

    @Column(name = "device_bound")
    @Builder.Default
    private Boolean deviceBound = false;

    @Column(name = "device_id", length = 100)
    @Size(max = 100)
    private String deviceId;

    @Column(name = "device_type", length = 50)
    @Size(max = 50)
    private String deviceType;

    @Column(name = "device_fingerprint", length = 255)
    @Size(max = 255)
    private String deviceFingerprint;

    @Column(name = "device_name", length = 100)
    @Size(max = 100)
    private String deviceName;

    @Column(name = "os_type", length = 30)
    @Size(max = 30)
    private String osType;

    @Column(name = "os_version", length = 30)
    @Size(max = 30)
    private String osVersion;

    // ========================================================================
    // DIGITAL WALLET
    // ========================================================================

    @Column(name = "is_wallet_token")
    @Builder.Default
    private Boolean isWalletToken = false;

    @Column(name = "wallet_provider", length = 50)
    @Size(max = 50)
    private String walletProvider;

    @Column(name = "wallet_account_id", length = 100)
    @Size(max = 100)
    private String walletAccountId;

    @Column(name = "wallet_device_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private java.math.BigDecimal walletDeviceScore;

    @Column(name = "wallet_account_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private java.math.BigDecimal walletAccountScore;

    // ========================================================================
    // PROVISIONING
    // ========================================================================

    @Column(name = "provisioning_status", length = 30)
    @Size(max = 30)
    private String provisioningStatus;

    @Column(name = "provisioning_date")
    private LocalDateTime provisioningDate;

    @Column(name = "provisioning_method", length = 50)
    @Size(max = 50)
    private String provisioningMethod;

    @Column(name = "activation_code", length = 50)
    @Size(max = 50)
    private String activationCode;

    @Column(name = "activation_code_expires_at")
    private LocalDateTime activationCodeExpiresAt;

    @Column(name = "is_activated")
    @Builder.Default
    private Boolean isActivated = false;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    // ========================================================================
    // CRYPTOGRAM
    // ========================================================================

    @Column(name = "supports_cryptogram")
    @Builder.Default
    private Boolean supportsCryptogram = true;

    @Column(name = "cryptogram_type", length = 30)
    @Size(max = 30)
    private String cryptogramType;

    @Column(name = "last_cryptogram_date")
    private LocalDateTime lastCryptogramDate;

    // ========================================================================
    // SECURITY
    // ========================================================================

    @Column(name = "token_assurance_level", length = 20)
    @Size(max = 20)
    private String tokenAssuranceLevel;

    @Column(name = "risk_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private java.math.BigDecimal riskScore;

    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;

    @Column(name = "kyc_verified")
    private Boolean kycVerified;

    // ========================================================================
    // SUSPENSION & DEACTIVATION
    // ========================================================================

    @Column(name = "is_suspended")
    @Builder.Default
    private Boolean isSuspended = false;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 255)
    @Size(max = 255)
    private String suspensionReason;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "deactivation_reason", length = 255)
    @Size(max = 255)
    private String deactivationReason;

    @Column(name = "deactivated_by", length = 100)
    @Size(max = 100)
    private String deactivatedBy;

    // ========================================================================
    // NOTIFICATION
    // ========================================================================

    @Column(name = "user_notified_on_creation")
    @Builder.Default
    private Boolean userNotifiedOnCreation = false;

    @Column(name = "user_notified_on_usage")
    @Builder.Default
    private Boolean userNotifiedOnUsage = false;

    @Column(name = "user_notified_on_suspension")
    @Builder.Default
    private Boolean userNotifiedOnSuspension = false;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_metadata", columnDefinition = "jsonb")
    private Map<String, Object> tokenMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if token is active
     */
    @Transient
    public boolean isActive() {
        return "ACTIVE".equals(tokenStatus) &&
               !isExpired &&
               !isSuspended &&
               deletedAt == null &&
               (expiresAt == null || LocalDateTime.now().isBefore(expiresAt));
    }

    /**
     * Check if token is expired
     */
    @Transient
    public boolean isTokenExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if usage limit reached
     */
    @Transient
    public boolean isUsageLimitReached() {
        if (maxUsageCount == null) {
            return false;
        }
        return currentUsageCount >= maxUsageCount;
    }

    /**
     * Check if can be used
     */
    @Transient
    public boolean canBeUsed() {
        return isActive() &&
               !isUsageLimitReached() &&
               isActivated;
    }

    /**
     * Increment usage count
     */
    public void incrementUsageCount() {
        this.currentUsageCount = (this.currentUsageCount == null ? 0 : this.currentUsageCount) + 1;
        this.lastUsedDate = LocalDateTime.now();

        // Check if single use token - deactivate after use
        if (singleUse) {
            deactivate("Single use token consumed");
        }

        // Check if max usage reached
        if (maxUsageCount != null && currentUsageCount >= maxUsageCount) {
            deactivate("Max usage count reached");
        }
    }

    /**
     * Activate token
     */
    public void activate() {
        this.isActivated = true;
        this.activatedAt = LocalDateTime.now();
        this.tokenStatus = "ACTIVE";
    }

    /**
     * Suspend token
     */
    public void suspend(String reason) {
        this.isSuspended = true;
        this.suspendedAt = LocalDateTime.now();
        this.suspensionReason = reason;
        this.tokenStatus = "SUSPENDED";
    }

    /**
     * Unsuspend token
     */
    public void unsuspend() {
        this.isSuspended = false;
        this.suspendedAt = null;
        this.suspensionReason = null;
        this.tokenStatus = "ACTIVE";
    }

    /**
     * Deactivate token
     */
    public void deactivate(String reason) {
        this.tokenStatus = "INACTIVE";
        this.deactivatedAt = LocalDateTime.now();
        this.deactivationReason = reason;
    }

    /**
     * Expire token
     */
    public void expire() {
        this.isExpired = true;
        this.tokenStatus = "EXPIRED";
    }

    /**
     * Bind to device
     */
    public void bindToDevice(String deviceId, String deviceType, String fingerprint) {
        this.deviceBound = true;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.deviceFingerprint = fingerprint;
    }

    /**
     * Provision to wallet
     */
    public void provisionToWallet(String provider, String accountId) {
        this.isWalletToken = true;
        this.walletProvider = provider;
        this.walletAccountId = accountId;
        this.provisioningStatus = "PROVISIONED";
        this.provisioningDate = LocalDateTime.now();
    }

    /**
     * Record cryptogram generation
     */
    public void recordCryptogramGeneration(String type) {
        this.cryptogramType = type;
        this.lastCryptogramDate = LocalDateTime.now();
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (tokenStatus == null) {
            tokenStatus = "ACTIVE";
        }
        if (createdAtTimestamp == null) {
            createdAtTimestamp = LocalDateTime.now();
        }
        if (isExpired == null) {
            isExpired = false;
        }
        if (singleUse == null) {
            singleUse = false;
        }
        if (multiUse == null) {
            multiUse = true;
        }
        if (currentUsageCount == null) {
            currentUsageCount = 0;
        }
        if (deviceBound == null) {
            deviceBound = false;
        }
        if (isWalletToken == null) {
            isWalletToken = false;
        }
        if (isActivated == null) {
            isActivated = false;
        }
        if (supportsCryptogram == null) {
            supportsCryptogram = true;
        }
        if (isSuspended == null) {
            isSuspended = false;
        }
        if (userNotifiedOnCreation == null) {
            userNotifiedOnCreation = false;
        }
        if (userNotifiedOnUsage == null) {
            userNotifiedOnUsage = false;
        }
        if (userNotifiedOnSuspension == null) {
            userNotifiedOnSuspension = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        // Check if token has expired
        if (!isExpired && isTokenExpired()) {
            expire();
        }
    }
}
