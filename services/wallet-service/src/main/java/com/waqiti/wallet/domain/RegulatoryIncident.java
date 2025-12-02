package com.waqiti.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Regulatory Incident Entity
 *
 * Represents incidents requiring reporting to regulatory authorities
 * (FinCEN, IRS, OFAC, SEC, etc.). Tracks the lifecycle from detection
 * through reporting and resolution.
 *
 * REGULATORY AUTHORITY TYPES:
 * - FinCEN: Suspicious Activity Reports (SARs)
 * - IRS: Form 8300 for cash transactions > $10K
 * - OFAC: Sanctions screening failures and violations
 * - SEC: Securities-related compliance incidents
 * - State Banking: State-specific regulatory requirements
 *
 * DATA RETENTION:
 * - Minimum 5-7 years depending on regulatory requirement
 * - Permanent retention for serious violations
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "regulatory_incidents")
public class RegulatoryIncident {

    /**
     * Unique incident identifier
     */
    @Id
    private String id;

    /**
     * User ID associated with incident
     */
    @Indexed
    private UUID userId;

    /**
     * Incident type
     * Examples: SANCTIONS_FREEZE_FAILURE, SAR_FILING_FAILURE, IRS_FORM_8300_FAILURE
     */
    @Indexed
    private String incidentType;

    /**
     * Incident severity
     * Values: CRITICAL, HIGH, MEDIUM, LOW
     */
    @Indexed
    private String severity;

    /**
     * Detailed description of the incident
     */
    private String description;

    /**
     * Associated case ID for cross-reference
     */
    @Indexed
    private String caseId;

    /**
     * Whether this incident requires regulator notification
     */
    @Indexed
    private boolean requiresRegulatorNotification;

    /**
     * Target regulatory authority (FinCEN, IRS, OFAC, SEC, etc.)
     */
    private String regulatoryAuthority;

    /**
     * Deadline for regulatory reporting
     */
    @Indexed
    private LocalDateTime reportingDeadline;

    /**
     * When incident was reported to regulator
     */
    private LocalDateTime reportedAt;

    /**
     * Reference number from regulatory filing
     */
    private String reportingReference;

    /**
     * Current incident status
     * Values: CREATED, UNDER_INVESTIGATION, PENDING_REPORT, REPORTED, RESOLVED, CLOSED
     */
    @Indexed
    private String status;

    /**
     * When incident was created
     */
    @Indexed
    private LocalDateTime createdAt;

    /**
     * When incident was resolved
     */
    private LocalDateTime resolvedAt;

    /**
     * Resolution description and actions taken
     */
    private String resolution;

    /**
     * Investigation notes
     */
    private String investigationNotes;

    /**
     * Root cause analysis
     */
    private String rootCause;

    /**
     * Corrective actions taken
     */
    private String correctiveActions;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Last updated by user
     */
    private String updatedBy;

    /**
     * Additional metadata (JSON)
     */
    private String metadata;

    /**
     * Check if incident is overdue for reporting
     */
    public boolean isOverdue() {
        if (reportingDeadline == null) return false;
        if ("REPORTED".equals(status) || "RESOLVED".equals(status) || "CLOSED".equals(status)) {
            return false;
        }
        return LocalDateTime.now().isAfter(reportingDeadline);
    }

    /**
     * Check if incident is critical
     */
    public boolean isCritical() {
        return "CRITICAL".equals(severity) || requiresRegulatorNotification;
    }

    /**
     * Get days until reporting deadline
     */
    public long getDaysUntilDeadline() {
        if (reportingDeadline == null) return Long.MAX_VALUE;
        return java.time.Duration.between(LocalDateTime.now(), reportingDeadline).toDays();
    }
}
