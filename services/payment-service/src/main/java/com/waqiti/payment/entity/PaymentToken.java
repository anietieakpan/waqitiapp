package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PRODUCTION-GRADE Payment Token Entity (PCI-DSS Compliant)
 *
 * SECURITY:
 * - Stores tokenized references to payment methods (NOT raw card data)
 * - Supports token expiration and revocation
 * - Optimistic locking with @Version for concurrency control
 * - Comprehensive audit fields
 * - Token fingerprinting for duplicate detection
 *
 * PCI-DSS COMPLIANCE:
 * - This table does NOT store raw card numbers (level 1 compliance)
 * - Tokens are opaque identifiers that reference external vault
 * - Token access is logged for audit trail
 * - Automatic token expiration reduces exposure window
 *
 * SUPPORTED TOKEN TYPES:
 * - CARD: Credit/debit card tokens (Stripe, PayPal, etc.)
 * - BANK_ACCOUNT: ACH bank account tokens
 * - CRYPTO_WALLET: Cryptocurrency wallet addresses
 * - APPLE_PAY: Apple Pay tokens
 * - GOOGLE_PAY: Google Pay tokens
 *
 * @author Waqiti Payment Security Team
 * @version 3.0 - Enterprise Production
 * @since 2025-10-11
 */
@Entity
@Table(name = "payment_tokens", indexes = {
    @Index(name = "idx_payment_tokens_user_id", columnList = "user_id"),
    @Index(name = "idx_payment_tokens_token", columnList = "token", unique = true),
    @Index(name = "idx_payment_tokens_fingerprint", columnList = "fingerprint"),
    @Index(name = "idx_payment_tokens_status", columnList = "status"),
    @Index(name = "idx_payment_tokens_user_status", columnList = "user_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * User who owns this payment token
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Opaque token identifier (NOT raw card number)
     * Format: tok_live_{UUID}_{checksum} or tok_test_{UUID}_{checksum}
     * Example: tok_live_550e8400-e29b-41d4-a716-446655440000_a3c9f1
     */
    @Column(name = "token", nullable = false, unique = true, length = 128)
    private String token;

    /**
     * Type of payment method this token represents
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 50)
    private TokenType tokenType;

    /**
     * Current status of the token
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TokenStatus status = TokenStatus.ACTIVE;

    /**
     * External payment provider (Stripe, PayPal, etc.)
     */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /**
     * Provider-specific token/customer ID
     */
    @Column(name = "provider_token_id", nullable = false, length = 255)
    private String providerTokenId;

    /**
     * Fingerprint for duplicate detection (hashed representation)
     * Example: For cards, hash of last4 + expMonth + expYear + brand
     */
    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    /**
     * Last 4 digits of card/account (for UI display only)
     */
    @Column(name = "last_four", length = 4)
    private String lastFour;

    /**
     * Payment method brand (Visa, Mastercard, Bank Name, etc.)
     */
    @Column(name = "brand", length = 50)
    private String brand;

    /**
     * Expiration month (for cards)
     */
    @Column(name = "exp_month")
    private Integer expMonth;

    /**
     * Expiration year (for cards)
     */
    @Column(name = "exp_year")
    private Integer expYear;

    /**
     * User-friendly label for this payment method
     * Example: "Work Visa •••• 4242", "Chase Checking •••• 1234"
     */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /**
     * Token expiration timestamp (security best practice)
     * Tokens automatically expire after period of inactivity
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * When this token was revoked (if applicable)
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Last time this token was used for a transaction
     */
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /**
     * Is this the user's default payment method?
     */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Additional metadata (JSON string)
     * Example: {"billing_address": {...}, "cardholder_name": "..."}
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Optimistic locking version (prevents concurrent modification issues)
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Audit: When this token was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Audit: When this token was last updated
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Audit: User/system that created this token
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Audit: User/system that last updated this token
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Token Type Enumeration
     */
    public enum TokenType {
        CARD,              // Credit/debit cards
        BANK_ACCOUNT,      // ACH bank accounts
        CRYPTO_WALLET,     // Cryptocurrency wallets
        APPLE_PAY,         // Apple Pay
        GOOGLE_PAY,        // Google Pay
        PAYPAL,            // PayPal account
        VENMO,             // Venmo account
        CASH_APP           // Cash App
    }

    /**
     * Token Status Enumeration
     */
    public enum TokenStatus {
        ACTIVE,            // Token is valid and can be used
        REVOKED,           // Token has been revoked by user/system
        EXPIRED,           // Token has expired
        FAILED_VALIDATION  // Token failed validation with provider
    }

    /**
     * Business logic: Check if token is usable
     */
    public boolean isUsable() {
        if (status != TokenStatus.ACTIVE) {
            return false;
        }

        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }

        // For cards, check expiration date
        if (tokenType == TokenType.CARD && expMonth != null && expYear != null) {
            LocalDateTime cardExpiration = LocalDateTime.of(expYear, expMonth, 1, 0, 0);
            if (LocalDateTime.now().isAfter(cardExpiration)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Business logic: Get masked display string for UI
     * Example: "Visa •••• 4242"
     */
    public String getMaskedDisplay() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        if (brand != null && lastFour != null) {
            return brand + " •••• " + lastFour;
        }

        if (lastFour != null) {
            return "•••• " + lastFour;
        }

        return "Payment Method";
    }
}
