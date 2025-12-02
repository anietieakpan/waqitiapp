package com.waqiti.tokenization.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Token Mapping Entity
 *
 * Stores the mapping between tokens and encrypted card data
 *
 * PCI-DSS Compliance Notes:
 * - No plaintext card numbers stored (encryptedCardData contains Vault ciphertext only)
 * - Last 4 digits stored (PCI-DSS allows this)
 * - Card BIN stored for fraud detection (PCI-DSS allows this)
 * - All sensitive data encrypted at rest via database-level encryption
 *
 * @author Waqiti Security Team
 */
@Entity
@Table(name = "token_mappings", indexes = {
    @Index(name = "idx_token_mapping_token", columnList = "token", unique = true),
    @Index(name = "idx_token_mapping_user", columnList = "user_id"),
    @Index(name = "idx_token_mapping_active", columnList = "active"),
    @Index(name = "idx_token_mapping_expires", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique token (format: tok_[32-char-uuid])
     */
    @Column(name = "token", nullable = false, unique = true, length = 40)
    private String token;

    /**
     * Encrypted card data (Vault Transit Engine ciphertext)
     * Format: vault:v1:base64encodedciphertext
     */
    @Column(name = "encrypted_card_data", nullable = false, length = 500)
    private String encryptedCardData;

    /**
     * User ID for authorization checks
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Last 4 digits of card (PCI-DSS allows storing this)
     */
    @Column(name = "last_4_digits", nullable = false, length = 4)
    private String last4Digits;

    /**
     * Card BIN (first 6 digits) for fraud detection
     */
    @Column(name = "card_bin", nullable = false, length = 6)
    private String cardBin;

    /**
     * Card type (VISA, MASTERCARD, AMEX, DISCOVER, etc.)
     */
    @Column(name = "card_type", nullable = false, length = 20)
    private String cardType;

    /**
     * Token active status
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Expiration date of the card
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Timestamp when token was revoked
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Creation timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version field for optimistic locking
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
