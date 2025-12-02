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
 * MFA Enrollment Response DTO
 *
 * Contains multi-factor authentication enrollment results including
 * setup details, backup codes, and verification requirements.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Strong authentication requirements
 * - SOC 2: Access control and identity verification
 * - NIST: Multi-factor authentication standards
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MFAEnrollmentResponse {

    /**
     * Enrollment successful flag
     */
    @NotNull
    private boolean successful;

    /**
     * User ID
     */
    @NotNull
    private UUID userId;

    /**
     * MFA method enrolled
     * Values: TOTP, SMS, EMAIL, AUTHENTICATOR_APP, HARDWARE_TOKEN, BIOMETRIC
     */
    @NotNull
    private String mfaMethod;

    /**
     * Enrollment status
     * Values: PENDING_VERIFICATION, ACTIVE, FAILED
     */
    @NotNull
    private String enrollmentStatus;

    /**
     * Secret key (for TOTP - shown only once)
     */
    private String secretKey;

    /**
     * QR code data URL (for authenticator apps)
     */
    private String qrCodeDataUrl;

    /**
     * Manual entry key (formatted for easy entry)
     */
    private String manualEntryKey;

    /**
     * Backup codes generated
     */
    private boolean backupCodesGenerated;

    /**
     * Backup codes (shown only once)
     */
    private List<String> backupCodes;

    /**
     * Number of backup codes
     */
    private int backupCodeCount;

    /**
     * Verification required
     */
    private boolean verificationRequired;

    /**
     * Verification code sent
     */
    private boolean verificationCodeSent;

    /**
     * Phone number (masked) for SMS MFA
     */
    private String maskedPhoneNumber;

    /**
     * Email address (masked) for email MFA
     */
    private String maskedEmail;

    /**
     * Enrollment method
     * Values: SELF_SERVICE, ADMIN_ENFORCED, AUTOMATIC
     */
    private String enrollmentMethod;

    /**
     * MFA enforced by policy
     */
    private boolean enforcedByPolicy;

    /**
     * Can be disabled by user
     */
    private boolean userCanDisable;

    /**
     * Grace period for MFA requirement (days)
     */
    private Integer gracePeriodDays;

    /**
     * Grace period expires at
     */
    private LocalDateTime gracePeriodExpiresAt;

    /**
     * Already enrolled methods
     */
    private List<String> alreadyEnrolledMethods;

    /**
     * Available MFA methods
     */
    private List<String> availableMfaMethods;

    /**
     * Primary MFA method
     */
    private String primaryMfaMethod;

    /**
     * Fallback MFA methods
     */
    private List<String> fallbackMfaMethods;

    /**
     * Device name/identifier
     */
    private String deviceName;

    /**
     * Device fingerprint
     */
    private String deviceFingerprint;

    /**
     * Trusted device flag
     */
    private boolean trustedDevice;

    /**
     * Remember device option available
     */
    private boolean rememberDeviceAvailable;

    /**
     * Instructions for setup
     */
    private String setupInstructions;

    /**
     * Next steps
     */
    private List<String> nextSteps;

    /**
     * Error message (if enrollment failed)
     */
    private String errorMessage;

    /**
     * Error code (if enrollment failed)
     */
    private String errorCode;

    /**
     * Enrolled at timestamp
     */
    @NotNull
    private LocalDateTime enrolledAt;

    /**
     * Last verified at
     */
    private LocalDateTime lastVerifiedAt;

    /**
     * MFA enrollment ID
     */
    private UUID enrollmentId;

    /**
     * Issuer name (for TOTP)
     */
    private String issuer;

    /**
     * Account name (for TOTP)
     */
    private String accountName;

    /**
     * Algorithm used (for TOTP)
     */
    private String algorithm;

    /**
     * Digits (for TOTP - typically 6)
     */
    private int digits;

    /**
     * Time step (for TOTP - typically 30 seconds)
     */
    private int timeStep;
}
