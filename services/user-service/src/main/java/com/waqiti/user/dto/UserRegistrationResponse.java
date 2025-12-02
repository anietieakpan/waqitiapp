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
 * User Registration Response DTO
 *
 * Contains user registration results including account details,
 * verification requirements, and next steps.
 *
 * COMPLIANCE RELEVANCE:
 * - GDPR: User data processing consent
 * - SOC 2: Account creation audit trail
 * - KYC/AML: Identity verification requirements
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationResponse {

    /**
     * User ID
     */
    @NotNull
    private UUID userId;

    /**
     * Username
     */
    @NotNull
    private String username;

    /**
     * Email address
     */
    @NotNull
    private String email;

    /**
     * Account status
     * Values: PENDING_VERIFICATION, ACTIVE, PENDING_KYC, PENDING_APPROVAL
     */
    @NotNull
    private String accountStatus;

    /**
     * Registration successful flag
     */
    @NotNull
    private boolean successful;

    /**
     * Email verification required
     */
    private boolean emailVerificationRequired;

    /**
     * Email verification token
     */
    private String emailVerificationToken;

    /**
     * Email verification sent
     */
    private boolean emailVerificationSent;

    /**
     * Phone verification required
     */
    private boolean phoneVerificationRequired;

    /**
     * Phone verification code sent
     */
    private boolean phoneVerificationSent;

    /**
     * KYC verification required
     */
    private boolean kycVerificationRequired;

    /**
     * KYC level required
     * Values: BASIC, ENHANCED, FULL
     */
    private String kycLevelRequired;

    /**
     * Document upload required
     */
    private boolean documentUploadRequired;

    /**
     * Required documents
     */
    private List<String> requiredDocuments;

    /**
     * Account approval required
     */
    private boolean approvalRequired;

    /**
     * Estimated approval time (hours)
     */
    private Integer estimatedApprovalTimeHours;

    /**
     * Initial role assigned
     */
    private String initialRole;

    /**
     * Welcome email sent
     */
    private boolean welcomeEmailSent;

    /**
     * Activation link
     */
    private String activationLink;

    /**
     * Activation link expires at
     */
    private LocalDateTime activationLinkExpiresAt;

    /**
     * Next steps for user
     */
    private List<String> nextSteps;

    /**
     * Registration method
     * Values: EMAIL, PHONE, SOCIAL, INVITE
     */
    private String registrationMethod;

    /**
     * Referral code used
     */
    private String referralCodeUsed;

    /**
     * Campaign ID
     */
    private String campaignId;

    /**
     * Error message (if registration failed)
     */
    private String errorMessage;

    /**
     * Error code (if registration failed)
     */
    private String errorCode;

    /**
     * Validation errors
     */
    private List<String> validationErrors;

    /**
     * Registration timestamp
     */
    @NotNull
    private LocalDateTime registeredAt;

    /**
     * User profile completion percentage
     */
    private int profileCompletionPercentage;

    /**
     * Temporary password provided
     */
    private boolean temporaryPasswordProvided;

    /**
     * Password change required on first login
     */
    private boolean passwordChangeRequired;

    /**
     * Terms accepted
     */
    private boolean termsAccepted;

    /**
     * Terms version accepted
     */
    private String termsVersionAccepted;

    /**
     * Privacy policy accepted
     */
    private boolean privacyPolicyAccepted;

    /**
     * Marketing consent given
     */
    private boolean marketingConsentGiven;

    /**
     * Account ID (if different from userId)
     */
    private UUID accountId;

    /**
     * IP address at registration
     */
    private String registrationIpAddress;

    /**
     * Device information
     */
    private String deviceInfo;
}
