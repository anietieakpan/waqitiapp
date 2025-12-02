package com.waqiti.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for freezing an account
 *
 * Account freeze is a critical security and compliance action that temporarily
 * suspends all transaction capabilities while maintaining account data integrity.
 *
 * Common Freeze Scenarios:
 * - Suspected fraud or unauthorized access
 * - Regulatory/compliance hold (AML investigation)
 * - User-requested security freeze
 * - Court order or legal hold
 * - Suspicious activity detected by fraud engine
 * - Account takeover prevention
 * - Dispute resolution hold
 *
 * Freeze Effects:
 * - All debits blocked (withdrawals, payments, transfers out)
 * - Credits may be allowed (configurable)
 * - Account balance preserved
 * - Existing scheduled transactions canceled
 * - User receives notification
 * - Audit trail created
 *
 * Business Rules:
 * - Freeze reason required (regulatory compliance)
 * - Admin/system authorization required
 * - Automatic unfreeze date optional (manual review by default)
 * - User notification mandatory
 * - Cannot freeze already frozen account (check status first)
 * - Cannot freeze closed accounts
 *
 * @author Production Readiness Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreezeAccountRequest {

    /**
     * ID of the account to freeze
     * Required for identifying the target account
     */
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    /**
     * Freeze reason category
     * Values: FRAUD_SUSPECTED, COMPLIANCE_HOLD, USER_REQUEST, LEGAL_HOLD,
     *         SUSPICIOUS_ACTIVITY, ACCOUNT_TAKEOVER, DISPUTE, REGULATORY, OTHER
     */
    @NotBlank(message = "Freeze reason is required")
    private String freezeReason;

    /**
     * Detailed explanation for the freeze
     * Required for audit trail and compliance documentation
     */
    @NotBlank(message = "Freeze description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String freezeDescription;

    /**
     * User ID initiating the freeze
     * Required: Admin, compliance officer, or automated system
     */
    @NotNull(message = "Initiator ID is required")
    private UUID initiatedBy;

    /**
     * Role/authority of the initiator
     * Values: ADMIN, COMPLIANCE_OFFICER, FRAUD_ANALYST, SYSTEM, LEGAL
     */
    @NotBlank(message = "Initiator role is required")
    private String initiatorRole;

    /**
     * Reference ID from external system
     * Optional: Case number, investigation ID, court order number
     */
    @Size(max = 100, message = "External reference cannot exceed 100 characters")
    private String externalReference;

    /**
     * Severity level of the freeze
     * Values: LOW, MEDIUM, HIGH, CRITICAL
     * Affects notification urgency and review priority
     */
    @NotBlank(message = "Severity level is required")
    private String severityLevel;

    /**
     * Automatic unfreeze date/time
     * Optional: If null, requires manual unfreeze (default)
     * For temporary freezes with known resolution date
     */
    private LocalDateTime autoUnfreezeAt;

    /**
     * Flag indicating if credits (deposits) are allowed during freeze
     * Default: true (allow incoming funds)
     * False for complete account lock (rare)
     */
    @Builder.Default
    private Boolean allowCredits = true;

    /**
     * Flag indicating if user should be notified
     * Default: true (notify user of freeze)
     * False for silent freeze (fraud investigation)
     */
    @Builder.Default
    private Boolean notifyUser = true;

    /**
     * Notification method preference
     * Values: EMAIL, SMS, PUSH, ALL, NONE
     * Only applicable if notifyUser = true
     */
    private String notificationMethod;

    /**
     * Flag indicating if this is an emergency freeze
     * True for immediate action (fraud, security breach)
     * Bypasses certain validation checks
     */
    @Builder.Default
    private Boolean emergencyFreeze = false;

    /**
     * IP address of the initiator (for audit)
     */
    @Size(max = 45, message = "IP address cannot exceed 45 characters")
    private String ipAddress;

    /**
     * Related incident/ticket ID
     * Optional: Links freeze to incident tracking system
     */
    private String incidentId;

    /**
     * Additional metadata (JSON format)
     * Optional: Extra context, evidence, system flags
     */
    private String metadata;

    /**
     * Flag indicating if scheduled transactions should be canceled
     * Default: true (cancel all scheduled transactions)
     */
    @Builder.Default
    private Boolean cancelScheduledTransactions = true;

    /**
     * Flag indicating if active holds should be released
     * Default: false (keep holds in place)
     * True to release all pending holds
     */
    @Builder.Default
    private Boolean releaseActiveHolds = false;

    /**
     * Expected resolution date
     * Optional: Estimated date for investigation completion
     * For planning and reporting purposes
     */
    private LocalDateTime expectedResolutionDate;

    /**
     * Compliance case type (for regulatory freezes)
     * Values: AML, KYC, SANCTIONS, CTF, OTHER
     */
    private String complianceCaseType;

    /**
     * Internal notes for compliance/security team
     * Not visible to user
     */
    @Size(max = 2000, message = "Internal notes cannot exceed 2000 characters")
    private String internalNotes;

    /**
     * Validates if this is a fraud-related freeze
     */
    public boolean isFraudRelated() {
        return "FRAUD_SUSPECTED".equals(freezeReason) ||
               "SUSPICIOUS_ACTIVITY".equals(freezeReason) ||
               "ACCOUNT_TAKEOVER".equals(freezeReason);
    }

    /**
     * Validates if this is a compliance/regulatory freeze
     */
    public boolean isComplianceRelated() {
        return "COMPLIANCE_HOLD".equals(freezeReason) ||
               "REGULATORY".equals(freezeReason) ||
               complianceCaseType != null;
    }

    /**
     * Validates if this is a legal freeze
     */
    public boolean isLegalHold() {
        return "LEGAL_HOLD".equals(freezeReason);
    }

    /**
     * Validates if this is a user-requested freeze
     */
    public boolean isUserRequested() {
        return "USER_REQUEST".equals(freezeReason);
    }

    /**
     * Checks if auto-unfreeze is configured
     */
    public boolean hasAutoUnfreeze() {
        return autoUnfreezeAt != null && autoUnfreezeAt.isAfter(LocalDateTime.now());
    }

    /**
     * Validates if freeze reason is valid
     */
    private boolean isValidFreezeReason(String reason) {
        return reason != null && reason.matches(
            "FRAUD_SUSPECTED|COMPLIANCE_HOLD|USER_REQUEST|LEGAL_HOLD|" +
            "SUSPICIOUS_ACTIVITY|ACCOUNT_TAKEOVER|DISPUTE|REGULATORY|OTHER"
        );
    }

    /**
     * Validates if severity level is valid
     */
    private boolean isValidSeverityLevel(String level) {
        return level != null && level.matches("LOW|MEDIUM|HIGH|CRITICAL");
    }

    /**
     * Validates if initiator role is valid
     */
    private boolean isValidInitiatorRole(String role) {
        return role != null && role.matches(
            "ADMIN|COMPLIANCE_OFFICER|FRAUD_ANALYST|SYSTEM|LEGAL|SUPERVISOR"
        );
    }

    /**
     * Comprehensive validation of the freeze request
     *
     * @return validation error message, or null if valid
     */
    public String getValidationError() {
        if (accountId == null) {
            return "Account ID is required";
        }

        if (freezeReason == null || !isValidFreezeReason(freezeReason)) {
            return "Invalid freeze reason: " + freezeReason;
        }

        if (freezeDescription == null || freezeDescription.trim().length() < 10) {
            return "Freeze description required (min 10 characters)";
        }

        if (initiatedBy == null) {
            return "Initiator ID is required";
        }

        if (initiatorRole == null || !isValidInitiatorRole(initiatorRole)) {
            return "Invalid initiator role: " + initiatorRole;
        }

        if (severityLevel == null || !isValidSeverityLevel(severityLevel)) {
            return "Invalid severity level: " + severityLevel;
        }

        // Auto-unfreeze date must be in the future
        if (autoUnfreezeAt != null && autoUnfreezeAt.isBefore(LocalDateTime.now())) {
            return "Auto-unfreeze date must be in the future";
        }

        // Emergency freeze requires high/critical severity
        if (Boolean.TRUE.equals(emergencyFreeze) &&
            !"HIGH".equals(severityLevel) && !"CRITICAL".equals(severityLevel)) {
            return "Emergency freeze requires HIGH or CRITICAL severity";
        }

        // Legal holds require external reference
        if ("LEGAL_HOLD".equals(freezeReason) &&
            (externalReference == null || externalReference.trim().isEmpty())) {
            return "Legal holds require external reference (court order, case number)";
        }

        // Compliance freezes should have case type
        if (isComplianceRelated() && complianceCaseType == null) {
            return "Compliance freezes require case type";
        }

        // Silent freezes (no notification) should have justification
        if (Boolean.FALSE.equals(notifyUser) && !isFraudRelated()) {
            return "Silent freeze (no notification) only allowed for fraud-related cases";
        }

        return null; // Valid
    }

    /**
     * Validates business rules for the freeze request
     *
     * @return true if request is valid, false otherwise
     */
    public boolean isValid() {
        return getValidationError() == null;
    }

    /**
     * Gets freeze priority based on severity and type
     * Returns: 1 (highest) to 4 (lowest)
     */
    public int getFreezePriority() {
        if (Boolean.TRUE.equals(emergencyFreeze) || "CRITICAL".equals(severityLevel)) {
            return 1;
        } else if ("HIGH".equals(severityLevel) || isFraudRelated()) {
            return 2;
        } else if ("MEDIUM".equals(severityLevel) || isComplianceRelated()) {
            return 3;
        } else {
            return 4;
        }
    }

    /**
     * Checks if this freeze requires immediate notification to authorities
     * True for legal holds and certain compliance cases
     */
    public boolean requiresAuthorityNotification() {
        return isLegalHold() ||
               (isComplianceRelated() && "SANCTIONS".equals(complianceCaseType));
    }
}
