package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL: PCI DSS Compliant Tokenized Card Entity
 * 
 * This entity stores ONLY non-sensitive card data in compliance with PCI DSS.
 * 
 * PCI DSS COMPLIANCE FEATURES:
 * - NO Primary Account Number (PAN) storage
 * - NO Card Verification Value (CVV) storage
 * - NO Track Data storage
 * - NO PIN data storage
 * 
 * ALLOWED DATA (per PCI DSS):
 * - Cardholder name
 * - Expiration date
 * - Last 4 digits of PAN
 * - Token value (irreversible)
 * - Card type/brand
 * - Metadata for payment processing
 * 
 * SECURITY FEATURES:
 * - Unique token per card
 * - Vault reference for secure PAN storage
 * - Audit trail for all operations
 * - Token expiration management
 * - Usage tracking
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Entity
@Table(name = "tokenized_cards", indexes = {
    @Index(name = "idx_tokenized_cards_token", columnList = "token", unique = true),
    @Index(name = "idx_tokenized_cards_user_id", columnList = "user_id"),
    @Index(name = "idx_tokenized_cards_user_token", columnList = "user_id, token", unique = true),
    @Index(name = "idx_tokenized_cards_last4_user", columnList = "last_four_digits, user_id"),
    @Index(name = "idx_tokenized_cards_active", columnList = "is_active"),
    @Index(name = "idx_tokenized_cards_expires", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizedCard {
    
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Secure token value - replaces PAN for all operations
     * Format: tok_1234567890123456 (format-preserving) or tok_random
     */
    @Column(name = "token", nullable = false, unique = true, length = 50)
    @NotBlank
    @Size(min = 10, max = 50)
    private String token;
    
    /**
     * Last 4 digits of PAN - ONLY safe digits for display
     * Used for customer identification and UI display
     */
    @Column(name = "last_four_digits", nullable = false, length = 4)
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$", message = "Last 4 digits must be exactly 4 numeric digits")
    private String last4Digits;
    
    /**
     * Card type/brand (VISA, MASTERCARD, AMEX, DISCOVER, etc.)
     * Derived from PAN BIN during tokenization
     */
    @Column(name = "card_type", nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    private String cardType;
    
    /**
     * Card expiry month (1-12)
     * Safe to store per PCI DSS
     */
    @Column(name = "expiry_month", nullable = false)
    @Min(1)
    @Max(12)
    private Integer expiryMonth;
    
    /**
     * Card expiry year (YYYY)
     * Safe to store per PCI DSS
     */
    @Column(name = "expiry_year", nullable = false)
    @Min(2024)
    @Max(2099)
    private Integer expiryYear;
    
    /**
     * Cardholder name (if provided)
     * Safe to store per PCI DSS
     */
    @Column(name = "cardholder_name", length = 100)
    @Size(max = 100)
    private String cardholderName;
    
    /**
     * User ID who owns this tokenized card
     * Critical for access control and data isolation
     */
    @Column(name = "user_id", nullable = false)
    @NotNull
    private UUID userId;
    
    /**
     * Vault path reference for encrypted PAN storage
     * CRITICAL: This points to HashiCorp Vault location
     * Must be protected and never exposed in API responses
     */
    @Column(name = "vault_path", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String vaultPath;
    
    /**
     * Token creation timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Token last update timestamp
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Token expiration date
     * Tokens should expire and be rotated for security
     */
    @Column(name = "expires_at", nullable = false)
    @NotNull
    private LocalDateTime expiresAt;
    
    /**
     * Token status - active/inactive/revoked
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * Token revocation timestamp
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    /**
     * Reason for token revocation
     */
    @Column(name = "revocation_reason", length = 100)
    @Size(max = 100)
    private String revocationReason;
    
    /**
     * Last time token was used for payment
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    /**
     * Number of times token has been used
     * For usage analytics and security monitoring
     */
    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;
    
    /**
     * Maximum allowed usage count (0 = unlimited)
     * For single-use or limited-use tokens
     */
    @Column(name = "max_usage_count")
    @Builder.Default
    private Integer maxUsageCount = 0;
    
    /**
     * Correlation ID for audit trails
     * Links tokenization operation to audit logs
     */
    @Column(name = "correlation_id", length = 50)
    @Size(max = 50)
    private String correlationId;
    
    /**
     * Token security level (STANDARD, HIGH, CRITICAL)
     * Determines access controls and audit requirements
     */
    @Column(name = "security_level", length = 20)
    @Builder.Default
    private String securityLevel = "STANDARD";
    
    /**
     * Issuing bank identifier (optional)
     */
    @Column(name = "issuing_bank", length = 50)
    @Size(max = 50)
    private String issuingBank;
    
    /**
     * Country code where card was issued (optional)
     */
    @Column(name = "issuing_country", length = 3)
    @Size(max = 3)
    private String issuingCountry;
    
    /**
     * Token environment (PRODUCTION, STAGING, SANDBOX)
     */
    @Column(name = "environment", length = 20)
    @Builder.Default
    private String environment = "PRODUCTION";
    
    /**
     * Version of tokenization algorithm used
     */
    @Column(name = "tokenization_version", length = 10)
    @Builder.Default
    private String tokenizationVersion = "1.0";
    
    /**
     * Custom metadata for business use cases
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * Compliance validation flag
     * Ensures token meets all PCI DSS requirements
     */
    @Column(name = "pci_compliant", nullable = false)
    @Builder.Default
    private Boolean pciCompliant = true;
    
    /**
     * Audit flags for monitoring
     */
    @Column(name = "audit_all_usage", nullable = false)
    @Builder.Default
    private Boolean auditAllUsage = true;
    
    /**
     * Network tokenization support
     * For Apple Pay, Google Pay, etc.
     */
    @Column(name = "network_token_id", length = 50)
    @Size(max = 50)
    private String networkTokenId;
    
    @Column(name = "network_provider", length = 20)
    @Size(max = 20)
    private String networkProvider;
    
    @Column(name = "network_token_status", length = 20)
    @Size(max = 20)
    private String networkTokenStatus;
    
    @Column(name = "network_token_provisioned_at")
    private LocalDateTime networkTokenProvisionedAt;
    
    /**
     * Risk assessment data
     */
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "fraud_score")
    private Integer fraudScore;
    
    @Column(name = "risk_assessment_date")
    private LocalDateTime riskAssessmentDate;
    
    /**
     * Geographic information
     */
    @Column(name = "tokenized_location", length = 100)
    @Size(max = 100)
    private String tokenizedLocation;
    
    @Column(name = "tokenized_ip_address", length = 45)
    @Size(max = 45)
    private String tokenizedIpAddress;
    
    /**
     * Device information (if applicable)
     */
    @Column(name = "device_fingerprint", length = 100)
    @Size(max = 100)
    private String deviceFingerprint;
    
    @Column(name = "device_type", length = 20)
    @Size(max = 20)
    private String deviceType;
    
    /**
     * Utility methods
     */
    
    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if token is active and valid
     */
    public boolean isValidForUse() {
        return isActive && !isExpired() && pciCompliant;
    }
    
    /**
     * Check if usage limit is reached
     */
    public boolean isUsageLimitReached() {
        return maxUsageCount > 0 && usageCount >= maxUsageCount;
    }
    
    /**
     * Get masked token for logging (shows only prefix and suffix)
     */
    public String getMaskedToken() {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
    
    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        return String.format("%s ending in %s", cardType, last4Digits);
    }
    
    /**
     * Check if card is expiring soon (within 30 days)
     */
    public boolean isExpiringSoon() {
        return expiresAt != null && LocalDateTime.now().plusDays(30).isAfter(expiresAt);
    }
    
    /**
     * Increment usage count
     */
    public void incrementUsage() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
        this.lastUsedAt = LocalDateTime.now();
    }
    
    /**
     * Security validation before persistence
     */
    @PrePersist
    @PreUpdate
    private void validateSecurity() {
        // Ensure no sensitive data is accidentally stored
        if (this.cardholderName != null && this.cardholderName.matches(".*[0-9]{13,19}.*")) {
            throw new IllegalStateException("PCI VIOLATION: PAN detected in cardholder name field");
        }
        
        if (this.metadata != null && this.metadata.matches(".*[0-9]{13,19}.*")) {
            throw new IllegalStateException("PCI VIOLATION: PAN detected in metadata field");
        }
        
        // Validate token format
        if (this.token != null && !this.token.startsWith("tok_")) {
            throw new IllegalStateException("Invalid token format - must start with 'tok_'");
        }
        
        // Ensure PCI compliance flag
        if (!Boolean.TRUE.equals(this.pciCompliant)) {
            throw new IllegalStateException("Token must be PCI compliant before persistence");
        }
    }
    
    @Override
    public String toString() {
        // Safe toString that doesn't expose sensitive data
        return String.format("TokenizedCard{id=%s, token=%s, cardType=%s, last4=%s, userId=%s, active=%s}", 
            id, getMaskedToken(), cardType, last4Digits, userId, isActive);
    }
}