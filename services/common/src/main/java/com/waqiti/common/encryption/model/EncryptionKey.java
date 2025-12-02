package com.waqiti.common.encryption.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Encryption key model for key management
 */
@Entity
@Table(name = "encryption_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String keyId;

    @Column(unique = true)
    private String alias;

    private String keyType;

    @Enumerated(EnumType.STRING)
    private KeyStatus status;

    private String algorithm;
    private int keySize;

    @Column(length = 4096)
    private String encryptedDataKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime lastRotatedAt;
    private String purpose;
    private String createdBy;
    private boolean isActive;
    
    /**
     * Key status enumeration
     */
    public enum KeyStatus {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        REVOKED,
        PENDING_ACTIVATION
    }
    
    /**
     * Key types
     */
    public static class Type {
        public static final String AES = "AES";
        public static final String RSA = "RSA";
        public static final String EC = "EC";
        public static final String HMAC = "HMAC";
    }
    
    /**
     * Check if key is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if key is usable
     */
    public boolean isUsable() {
        return isActive && status == KeyStatus.ACTIVE && !isExpired();
    }
}