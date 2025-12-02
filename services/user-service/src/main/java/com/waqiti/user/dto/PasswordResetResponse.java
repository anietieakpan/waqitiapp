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
 * Password Reset Response DTO
 *
 * Contains password reset request results including reset token,
 * verification requirements, and security information.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Secure password reset process
 * - SOC 2: Access security and identity verification
 * - GDPR: User data access security
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetResponse {

    /**
     * Request successful flag
     */
    @NotNull
    private boolean successful;

    /**
     * Reset token ID
     */
    private UUID resetTokenId;

    /**
     * Reset token (not included for security - sent via email/SMS)
     */
    private String resetToken;

    /**
     * Token generated flag
     */
    private boolean tokenGenerated;

    /**
     * Token sent flag
     */
    private boolean tokenSent;

    /**
     * Token delivery method
     * Values: EMAIL, SMS, BOTH
     */
    private String tokenDeliveryMethod;

    /**
     * Email address (masked) where token was sent
     */
    private String maskedEmail;

    /**
     * Phone number (masked) where token was sent
     */
    private String maskedPhoneNumber;

    /**
     * Token expires at
     */
    private LocalDateTime tokenExpiresAt;

    /**
     * Token expiry duration (minutes)
     */
    private int tokenExpiryMinutes;

    /**
     * Additional verification required
     */
    private boolean additionalVerificationRequired;

    /**
     * Verification methods available
     * Values: SECURITY_QUESTIONS, EMAIL_CODE, SMS_CODE, BACKUP_CODE
     */
    private List<String> verificationMethods;

    /**
     * Security questions required
     */
    private boolean securityQuestionsRequired;

    /**
     * Security questions (if required)
     */
    private List<String> securityQuestions;

    /**
     * MFA required for reset
     */
    private boolean mfaRequired;

    /**
     * Reset attempts remaining
     */
    private int resetAttemptsRemaining;

    /**
     * Next reset allowed at
     */
    private LocalDateTime nextResetAllowedAt;

    /**
     * Rate limit applied
     */
    private boolean rateLimitApplied;

    /**
     * User ID (not included for security)
     */
    private UUID userId;

    /**
     * Username/identifier used for reset
     */
    private String identifier;

    /**
     * Reset method
     * Values: EMAIL, PHONE, SECURITY_QUESTION, ADMIN
     */
    private String resetMethod;

    /**
     * Verification method
     * Values: TOKEN, LINK, CODE, QUESTION
     */
    private String verificationMethod;

    /**
     * Reset link sent (vs code)
     */
    private boolean resetLinkSent;

    /**
     * Reset link URL (only if sending via secure channel)
     */
    private String resetLink;

    /**
     * Instructions message
     */
    private String instructions;

    /**
     * Error message (if reset failed)
     */
    private String errorMessage;

    /**
     * Error code (if reset failed)
     */
    private String errorCode;

    /**
     * Account locked flag
     */
    private boolean accountLocked;

    /**
     * Lockout reason (if account locked)
     */
    private String lockoutReason;

    /**
     * Lockout expires at
     */
    private LocalDateTime lockoutExpiresAt;

    /**
     * Contact support message
     */
    private String contactSupportMessage;

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
     * Suspicious activity detected
     */
    private boolean suspiciousActivityDetected;

    /**
     * Manual review required
     */
    private boolean manualReviewRequired;

    /**
     * Request timestamp
     */
    @NotNull
    private LocalDateTime requestedAt;

    /**
     * IP address
     */
    private String ipAddress;

    /**
     * Device fingerprint
     */
    private String deviceFingerprint;
}
