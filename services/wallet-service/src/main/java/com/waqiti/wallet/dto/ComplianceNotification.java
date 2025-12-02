package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Compliance Notification DTO
 *
 * Data transfer object for sending compliance notifications to officers
 * and regulatory teams. Contains all necessary context for incident
 * assessment and response.
 *
 * PRIORITY LEVELS:
 * - CRITICAL: Requires immediate response (< 5 min), escalation to on-call
 * - HIGH: Requires urgent response (< 30 min), team notification
 * - MEDIUM: Requires standard response (< 2 hours), team notification
 * - LOW: Standard tracking, daily review
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceNotification {

    /**
     * User ID associated with the compliance incident
     */
    private UUID userId;

    /**
     * Incident type classification
     * Examples: CRITICAL_FREEZE_FAILURE, SAR_FILING_FAILURE, SANCTIONS_MATCH
     */
    private String incidentType;

    /**
     * Compliance severity level
     * Values: CRITICAL_REGULATORY, HIGH_REGULATORY, HIGH_SECURITY, MEDIUM_OPERATIONAL
     */
    private String severity;

    /**
     * Root cause or freeze reason
     * Examples: SANCTIONS_MATCH, AML_SUSPICIOUS_ACTIVITY, OFAC_VIOLATION
     */
    private String freezeReason;

    /**
     * Associated case ID for tracking
     */
    private String caseId;

    /**
     * Brief error summary for quick assessment
     */
    private String errorSummary;

    /**
     * Whether this notification requires immediate action
     */
    private boolean requiresImmediateAction;

    /**
     * Whether this incident is regulatory reportable
     */
    private Boolean isRegulatoryReportable;

    /**
     * Notification priority level
     */
    private Priority priority;

    /**
     * SLA hours for resolution
     */
    private Integer slaHours;

    /**
     * Additional context or notes
     */
    private String additionalContext;

    /**
     * Priority enum for compliance notifications
     */
    public enum Priority {
        CRITICAL,   // Immediate on-call response required
        HIGH,       // Urgent team notification
        MEDIUM,     // Standard team notification
        LOW         // Standard tracking
    }
}
