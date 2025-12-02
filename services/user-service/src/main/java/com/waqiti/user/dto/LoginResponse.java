package com.waqiti.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Login Response DTO
 *
 * Contains authentication results including tokens, user information,
 * session details, and security flags.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Secure authentication and session management
 * - SOC 2: Access control and identity verification
 * - GDPR: User data privacy in authentication
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Login successful flag
     */
    @NotNull
    private boolean successful;

    /**
     * User ID
     */
    private UUID userId;

    /**
     * Username
     */
    private String username;

    /**
     * Email address
     */
    private String email;

    /**
     * Authentication token (JWT)
     */
    private String authToken;

    /**
     * Refresh token
     */
    private String refreshToken;

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Token expiration time
     */
    private LocalDateTime tokenExpiresAt;

    /**
     * Refresh token expiration time
     */
    private LocalDateTime refreshTokenExpiresAt;

    /**
     * User role
     */
    private String role;

    /**
     * User permissions
     */
    private List<String> permissions;

    /**
     * Requires MFA flag
     */
    private boolean requiresMFA;

    /**
     * MFA challenge ID (if MFA required)
     */
    private String mfaChallengeId;

    /**
     * MFA methods available
     */
    private List<String> mfaMethods;

    /**
     * Account status
     * Values: ACTIVE, LOCKED, SUSPENDED, PENDING_VERIFICATION
     */
    private String accountStatus;

    /**
     * First login flag
     */
    private boolean firstLogin;

    /**
     * Password expiration warning
     */
    private boolean passwordExpiringSoon;

    /**
     * Days until password expires
     */
    private Integer daysUntilPasswordExpires;

    /**
     * Last login timestamp
     */
    private LocalDateTime lastLoginAt;

    /**
     * Last login IP address
     */
    private String lastLoginIpAddress;

    /**
     * Login timestamp
     */
    @NotNull
    private LocalDateTime loginTimestamp;

    /**
     * Login IP address
     */
    private String loginIpAddress;

    /**
     * Device fingerprint
     */
    private String deviceFingerprint;

    /**
     * Login method
     * Values: PASSWORD, SSO, BIOMETRIC, API_KEY
     */
    private String loginMethod;

    /**
     * Failure reason (if login failed)
     */
    private String failureReason;

    /**
     * Error code (if login failed)
     */
    private String errorCode;

    /**
     * Failed attempt count
     */
    private int failedAttemptCount;

    /**
     * Account locked flag
     */
    private boolean accountLocked;

    /**
     * Lockout expires at
     */
    private LocalDateTime lockoutExpiresAt;

    /**
     * Security warnings
     */
    private List<String> securityWarnings;

    /**
     * Terms acceptance required
     */
    private boolean termsAcceptanceRequired;

    /**
     * Profile completion required
     */
    private boolean profileCompletionRequired;

    /**
     * Risk score (0-100)
     */
    private int riskScore;

    /**
     * Risk level
     * Values: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String riskLevel;

    /**
     * Notification count
     */
    private int notificationCount;

    /**
     * User preferences
     */
    private java.util.Map<String, String> userPreferences;
}
