package com.waqiti.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enterprise-grade Refresh Token entity for secure token rotation.
 *
 * Security Features:
 * - Token rotation on each use (prevents token replay)
 * - Token family tracking (detects stolen tokens)
 * - Automatic expiration
 * - Device binding
 * - IP address tracking
 * - Revocation support
 *
 * Token Rotation Strategy:
 * 1. Client uses refresh token to get new access token
 * 2. Server generates new refresh token
 * 3. Old refresh token is revoked
 * 4. If old token is reused, entire token family is revoked (breach detection)
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_token", columnList = "token", unique = true),
    @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_family", columnList = "token_family"),
    @Index(name = "idx_refresh_tokens_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token", unique = true, nullable = false, length = 500)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_family", nullable = false)
    private UUID tokenFamily; // All tokens in rotation chain share same family ID

    @Column(name = "parent_token_id")
    private UUID parentTokenId; // Previous token in rotation chain

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // Device and security context
    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType; // WEB, MOBILE_IOS, MOBILE_ANDROID

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "geolocation", length = 100)
    private String geolocation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // Business methods
    public boolean isValid() {
        return !revoked
            && !used
            && LocalDateTime.now().isBefore(expiresAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void markAsUsed() {
        this.used = true;
        this.usedAt = LocalDateTime.now();
    }

    public void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
        this.revocationReason = reason;
    }

    public void revokeTokenFamily() {
        revoke("Token family compromised - possible token theft detected");
    }
}
