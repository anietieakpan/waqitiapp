package com.waqiti.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enterprise-grade User entity with comprehensive security features.
 *
 * Security Features:
 * - Account locking after failed login attempts
 * - Password expiration and history tracking
 * - Two-factor authentication support
 * - Email and phone verification
 * - Session management
 * - Audit trail (created/updated timestamps)
 * - Soft delete support
 * - Optimistic locking for concurrent updates
 *
 * Compliance:
 * - GDPR: Soft delete, data retention
 * - PCI-DSS: Secure credential storage
 * - SOX: Audit trail, separation of duties
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_email", columnList = "email", unique = true),
    @Index(name = "idx_users_phone", columnList = "phone_number"),
    @Index(name = "idx_users_status", columnList = "account_status"),
    @Index(name = "idx_users_created", columnList = "created_at"),
    @Index(name = "idx_users_deleted", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", unique = true, nullable = false, length = 100)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "two_factor_enabled", nullable = false)
    @Builder.Default
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret", length = 64)
    private String twoFactorSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_method", length = 20)
    private TwoFactorMethod twoFactorMethod;

    // Account Security
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    @Column(name = "last_password_change_at")
    private LocalDateTime lastPasswordChangeAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    // Roles and Permissions
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Audit fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    // Optimistic locking
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // Additional metadata
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "locale", length = 10)
    @Builder.Default
    private String locale = "en_US";

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";

    // UserDetails implementation
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(permission -> new SimpleGrantedAuthority(permission.getName()))
            .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !deleted && accountStatus != AccountStatus.EXPIRED;
    }

    @Override
    public boolean isAccountNonLocked() {
        if (accountLockedUntil == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(accountLockedUntil);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        if (passwordExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isBefore(passwordExpiresAt);
    }

    @Override
    public boolean isEnabled() {
        return !deleted && accountStatus == AccountStatus.ACTIVE && emailVerified;
    }

    // Business methods
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            lockAccount(30); // Lock for 30 minutes
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLockedUntil = null;
    }

    public void lockAccount(int minutes) {
        this.accountLockedUntil = LocalDateTime.now().plusMinutes(minutes);
        this.accountStatus = AccountStatus.LOCKED;
    }

    public void unlockAccount() {
        this.accountLockedUntil = null;
        this.accountStatus = AccountStatus.ACTIVE;
        this.failedLoginAttempts = 0;
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.accountStatus = AccountStatus.DELETED;
    }

    public void updateLastLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ipAddress;
        resetFailedLoginAttempts();
    }

    public boolean requiresPasswordChange() {
        if (passwordExpiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(passwordExpiresAt);
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.lastPasswordChangeAt = LocalDateTime.now();
        this.passwordExpiresAt = LocalDateTime.now().plusDays(90); // 90-day password rotation
    }

    // Enums
    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        LOCKED,
        SUSPENDED,
        PENDING_VERIFICATION,
        EXPIRED,
        DELETED
    }

    public enum TwoFactorMethod {
        TOTP,          // Time-based One-Time Password (Google Authenticator)
        SMS,           // SMS verification
        EMAIL,         // Email verification
        HARDWARE_KEY,  // YubiKey, etc.
        BIOMETRIC      // Fingerprint, Face ID
    }
}
