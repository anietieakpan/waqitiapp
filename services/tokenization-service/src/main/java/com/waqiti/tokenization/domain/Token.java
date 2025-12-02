package com.waqiti.tokenization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Token Entity - Represents a tokenized sensitive data record
 *
 * PCI-DSS Compliance:
 * - Stores encrypted sensitive data
 * - Never stores plaintext card data
 * - Comprehensive audit trail
 *
 * Security:
 * - token field is unique, indexed, and immutable
 * - encryptedData is encrypted with AWS KMS
 * - No direct access to decrypted data
 *
 * @author Waqiti Platform Engineering
 */
@Entity
@Table(name = "tokens", indexes = {
    @Index(name = "idx_token", columnList = "token", unique = true),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_expires_at", columnList = "expiresAt"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The token value (cryptographically random, 32 characters)
     * Format: {TYPE}_{32_random_chars}
     * Example: CARD_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /**
     * Encrypted sensitive data (encrypted with AWS KMS)
     * Never stored in plaintext
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedData;

    /**
     * Token type (CARD, BANK_ACCOUNT, SSN, TAX_ID, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TokenType type;

    /**
     * User ID who owns this token
     */
    @Column(nullable = false)
    private String userId;

    /**
     * AWS KMS Key ID used for encryption
     */
    @Column(nullable = false)
    private String kmsKeyId;

    /**
     * Token status (ACTIVE, EXPIRED, REVOKED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenStatus status;

    /**
     * Token expiration timestamp
     * Cards: 90 days
     * Bank Accounts: 365 days
     * SSN/Tax ID: 10 years
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Last time this token was used (for audit)
     */
    @Column
    private Instant lastUsedAt;

    /**
     * Number of times this token has been used
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    /**
     * Optional metadata (JSON format)
     * e.g., {"last4": "1234", "brand": "visa"}
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * IP address from where token was created (for audit)
     */
    @Column(length = 45)
    private String createdFromIp;

    /**
     * User agent from where token was created (for audit)
     */
    @Column(length = 500)
    private String createdUserAgent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if token is active
     */
    public boolean isActive() {
        return status == TokenStatus.ACTIVE && !isExpired();
    }

    /**
     * Increment usage count
     */
    public void incrementUsage() {
        this.usageCount++;
        this.lastUsedAt = Instant.now();
    }
}
