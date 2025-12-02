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
 * MFA Verification Response DTO
 *
 * Contains multi-factor authentication verification results including
 * verification status, attempts, and session information.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Strong authentication verification
 * - SOC 2: Access control verification
 * - NIST: MFA verification standards
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MFAVerificationResponse {

    /**
     * Verification successful flag
     */
    @NotNull
    private boolean successful;

    /**
     * User ID
     */
    @NotNull
    private UUID userId;

    /**
     * MFA method used
     * Values: TOTP, SMS, EMAIL, AUTHENTICATOR_APP, HARDWARE_TOKEN, BIOMETRIC, BACKUP_CODE
     */
    @NotNull
    private String mfaMethod;

    /**
     * Verification status
     * Values: VERIFIED, FAILED, LOCKED, EXPIRED
     */
    @NotNull
    private String verificationStatus;

    /**
     * Authentication token (if verification successful)
     */
    private String authToken;

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Token expires at
     */
    private LocalDateTime tokenExpiresAt;

    /**
     * Failed attempt count
     */
    private int failedAttemptCount;

    /**
     * Remaining attempts
     */
    private int remainingAttempts;

    /**
     * Max attempts allowed
     */
    private int maxAttemptsAllowed;

    /**
     * Account locked flag
     */
    private boolean accountLocked;

    /**
     * Lockout expires at
     */
    private LocalDateTime lockoutExpiresAt;

    /**
     * Lockout duration (minutes)
     */
    private int lockoutDurationMinutes;

    /**
     * Failure reason
     */
    private String failureReason;

    /**
     * Error code
     */
    private String errorCode;

    /**
     * Error message
     */
    private String errorMessage;

    /**
     * Code expired flag
     */
    private boolean codeExpired;

    /**
     * Invalid code format
     */
    private boolean invalidCodeFormat;

    /**
     * Backup code used
     */
    private boolean backupCodeUsed;

    /**
     * Remaining backup codes
     */
    private Integer remainingBackupCodes;

    /**
     * New backup codes needed
     */
    private boolean newBackupCodesNeeded;

    /**
     * Device trusted flag
     */
    private boolean deviceTrusted;

    /**
     * Remember device option available
     */
    private boolean rememberDeviceAvailable;

    /**
     * Device remembered flag
     */
    private boolean deviceRemembered;

    /**
     * Device remember duration (days)
     */
    private Integer deviceRememberDurationDays;

    /**
     * Trust expires at
     */
    private LocalDateTime trustExpiresAt;

    /**
     * Fallback methods available
     */
    private List<String> fallbackMethodsAvailable;

    /**
     * Time window remaining (for TOTP)
     */
    private Integer timeWindowRemaining;

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
     * Anomaly detected flag
     */
    private boolean anomalyDetected;

    /**
     * Anomaly details
     */
    private List<String> anomalyDetails;

    /**
     * Step-up authentication required
     */
    private boolean stepUpAuthRequired;

    /**
     * Additional verification needed
     */
    private boolean additionalVerificationNeeded;

    /**
     * Verification methods available
     */
    private List<String> verificationMethodsAvailable;

    /**
     * Verified at timestamp
     */
    @NotNull
    private LocalDateTime verifiedAt;

    /**
     * IP address
     */
    private String ipAddress;

    /**
     * Device fingerprint
     */
    private String deviceFingerprint;

    /**
     * User agent
     */
    private String userAgent;

    /**
     * Geolocation
     */
    private String geolocation;

    /**
     * Previous verification timestamp
     */
    private LocalDateTime previousVerificationAt;

    /**
     * Verification ID
     */
    private UUID verificationId;
}
