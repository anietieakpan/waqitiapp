package com.waqiti.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enterprise-grade User Session entity for session management.
 *
 * Features:
 * - Multi-device session tracking
 * - Session timeout management
 * - Suspicious activity detection
 * - Force logout capability
 * - Session analytics
 *
 * Security:
 * - IP address validation
 * - User agent validation
 * - Geolocation tracking
 * - Concurrent session limits
 * - Automatic session cleanup
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_sessions_user", columnList = "user_id"),
    @Index(name = "idx_sessions_token", columnList = "session_token", unique = true),
    @Index(name = "idx_sessions_status", columnList = "status"),
    @Index(name = "idx_sessions_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_token", unique = true, nullable = false, length = 500)
    private String sessionToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    // Device information
    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    // Network information
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "geolocation", length = 100)
    private String geolocation;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 100)
    private String city;

    // Security flags
    @Column(name = "is_suspicious", nullable = false)
    @Builder.Default
    private Boolean isSuspicious = false;

    @Column(name = "suspicious_reason", length = 500)
    private String suspiciousReason;

    @Column(name = "trusted_device", nullable = false)
    @Builder.Default
    private Boolean trustedDevice = false;

    @Column(name = "two_factor_verified", nullable = false)
    @Builder.Default
    private Boolean twoFactorVerified = false;

    // Session metadata
    @Column(name = "login_method", length = 50)
    private String loginMethod; // PASSWORD, BIOMETRIC, SSO, etc.

    @Column(name = "activity_count", nullable = false)
    @Builder.Default
    private Long activityCount = 0L;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "termination_reason", length = 255)
    private String terminationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // Business methods
    public boolean isActive() {
        return status == SessionStatus.ACTIVE
            && LocalDateTime.now().isBefore(expiresAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
        this.activityCount++;
    }

    public void terminate(String reason) {
        this.status = SessionStatus.TERMINATED;
        this.terminatedAt = LocalDateTime.now();
        this.terminationReason = reason;
    }

    public void markAsSuspicious(String reason) {
        this.isSuspicious = true;
        this.suspiciousReason = reason;
    }

    public void extendSession(int hours) {
        this.expiresAt = LocalDateTime.now().plusHours(hours);
    }

    public boolean requiresTwoFactorVerification() {
        return !twoFactorVerified && user.getTwoFactorEnabled();
    }

    // Enums
    public enum SessionStatus {
        ACTIVE,
        EXPIRED,
        TERMINATED,
        SUSPENDED,
        REVOKED
    }
}
