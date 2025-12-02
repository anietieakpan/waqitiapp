package com.waqiti.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User Data Deletion Response DTO
 *
 * Contains results of user data deletion request (GDPR Right to be Forgotten)
 * including deletion status, scope, and compliance information.
 *
 * COMPLIANCE RELEVANCE:
 * - GDPR: Right to erasure (Right to be Forgotten)
 * - CCPA: Right to deletion
 * - SOC 2: Data lifecycle management
 * - Data retention policies
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDataDeletionResponse {

    /**
     * Deletion request ID
     */
    @NotNull
    private UUID deletionRequestId;

    /**
     * User ID
     */
    @NotNull
    private UUID userId;

    /**
     * Deletion status
     * Values: INITIATED, SCHEDULED, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED
     */
    @NotNull
    private String deletionStatus;

    /**
     * Deletion successful flag
     */
    private boolean successful;

    /**
     * Deletion reason
     */
    private String deletionReason;

    /**
     * Requested data categories for deletion
     */
    private List<String> requestedDataCategories;

    /**
     * Deleted data categories
     */
    private List<String> deletedDataCategories;

    /**
     * Retained data categories (with reasons)
     */
    private Map<String, String> retainedDataCategories;

    /**
     * Retention override applied
     */
    private boolean retentionOverrideApplied;

    /**
     * Retention override reason
     */
    private String retentionOverrideReason;

    /**
     * Legal hold flag
     */
    private boolean legalHold;

    /**
     * Legal hold reason
     */
    private String legalHoldReason;

    /**
     * Deletion scope
     * Values: FULL, PARTIAL, PROFILE_ONLY, TRANSACTIONS_ONLY
     */
    private String deletionScope;

    /**
     * Immediate deletion flag
     */
    private boolean immediateDeletion;

    /**
     * Scheduled deletion date
     */
    private LocalDateTime scheduledDeletionDate;

    /**
     * Grace period (days)
     */
    private Integer gracePeriodDays;

    /**
     * Grace period expires at
     */
    private LocalDateTime gracePeriodExpiresAt;

    /**
     * Cancellable until
     */
    private LocalDateTime cancellableUntil;

    /**
     * Account deactivated
     */
    private boolean accountDeactivated;

    /**
     * Account anonymized
     */
    private boolean accountAnonymized;

    /**
     * Backup data deleted
     */
    private boolean backupDataDeleted;

    /**
     * Backup deletion scheduled date
     */
    private LocalDateTime backupDeletionScheduledDate;

    /**
     * Third-party data deletion requested
     */
    private boolean thirdPartyDeletionRequested;

    /**
     * Third-party systems notified
     */
    private List<String> thirdPartySystemsNotified;

    /**
     * Deletion verification performed
     */
    private boolean deletionVerificationPerformed;

    /**
     * Verification timestamp
     */
    private LocalDateTime verificationTimestamp;

    /**
     * Deletion certificate ID
     */
    private UUID deletionCertificateId;

    /**
     * Certificate available
     */
    private boolean certificateAvailable;

    /**
     * Certificate download URL
     */
    private String certificateDownloadUrl;

    /**
     * Deleted entities count
     */
    private Map<String, Integer> deletedEntitiesCount;

    /**
     * Deletion errors
     */
    private List<DeletionError> deletionErrors;

    /**
     * Partial deletion reasons
     */
    private List<String> partialDeletionReasons;

    /**
     * Requested by (user ID)
     */
    @NotNull
    private UUID requestedBy;

    /**
     * Approved by (admin user ID if required)
     */
    private UUID approvedBy;

    /**
     * Approval timestamp
     */
    private LocalDateTime approvedAt;

    /**
     * Approval required flag
     */
    private boolean approvalRequired;

    /**
     * Deletion initiated timestamp
     */
    @NotNull
    private LocalDateTime deletionInitiatedAt;

    /**
     * Deletion started timestamp
     */
    private LocalDateTime deletionStartedAt;

    /**
     * Deletion completed timestamp
     */
    private LocalDateTime deletionCompletedAt;

    /**
     * Deletion duration (ms)
     */
    private Long deletionDurationMs;

    /**
     * Notification sent to user
     */
    private boolean notificationSentToUser;

    /**
     * Email confirmation sent
     */
    private boolean emailConfirmationSent;

    /**
     * Compliance report generated
     */
    private boolean complianceReportGenerated;

    /**
     * Compliance report ID
     */
    private UUID complianceReportId;

    /**
     * Audit trail ID
     */
    private UUID auditTrailId;

    /**
     * Audit logged flag
     */
    private boolean auditLogged;

    /**
     * Regulatory reporting required
     */
    private boolean regulatoryReportingRequired;

    /**
     * Regulatory reports submitted
     */
    private List<String> regulatoryReportsSubmitted;

    /**
     * Data retention period (days) for audit logs
     */
    private int auditRetentionPeriodDays;

    /**
     * Re-registration allowed
     */
    private boolean reRegistrationAllowed;

    /**
     * Re-registration cooldown (days)
     */
    private Integer reRegistrationCooldownDays;

    /**
     * Error message (if deletion failed)
     */
    private String errorMessage;

    /**
     * Error code (if deletion failed)
     */
    private String errorCode;

    /**
     * Contact support message
     */
    private String contactSupportMessage;

    /**
     * Next steps
     */
    private List<String> nextSteps;

    /**
     * Deletion Error nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeletionError {
        private String dataCategory;
        private String errorCode;
        private String errorMessage;
        private String system;
        private boolean retryable;
    }
}
