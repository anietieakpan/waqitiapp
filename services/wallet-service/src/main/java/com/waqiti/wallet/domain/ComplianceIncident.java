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
 * Compliance Incident Entity
 *
 * Represents compliance incidents requiring investigation, resolution,
 * and potential regulatory reporting. Tracks freeze failures, sanctions
 * violations, AML alerts, and other compliance-critical events.
 *
 * REGULATORY CONTEXT:
 * - Account freeze failures may indicate inability to comply with OFAC/sanctions
 * - SAR filing failures are FinCEN compliance violations
 * - Form 8300 filing failures are IRS compliance violations
 * - All incidents require audit trail for regulatory examination
 *
 * DATA RETENTION:
 * - Minimum 5 years retention (standard fintech compliance requirement)
 * - 7 years for tax-related incidents (Form 8300)
 * - Permanent retention for regulatory-reported incidents
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "compliance_incidents")
public class ComplianceIncident {

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
     * Incident type classification
     * Examples: FREEZE_FAILURE, SAR_FILING_FAILURE, SANCTIONS_MATCH, AML_ALERT
     */
    @Indexed
    private String incidentType;

    /**
     * Compliance severity level
     * Values: CRITICAL_REGULATORY, HIGH_REGULATORY, HIGH_SECURITY, MEDIUM_OPERATIONAL
     */
    @Indexed
    private String severity;

    /**
     * Root cause or reason for incident
     * Examples: SANCTIONS_MATCH, AML_SUSPICIOUS_ACTIVITY, OFAC_VIOLATION
     */
    private String freezeReason;

    /**
     * Scope of freeze/incident
     * Examples: FULL_FREEZE, DEBIT_ONLY, HIGH_VALUE_ONLY
     */
    private String scope;

    /**
     * Associated case ID for tracking
     */
    @Indexed
    private String caseId;

    /**
     * Full event data that triggered incident (JSON string)
     */
    private String eventData;

    /**
     * Error analysis details (can be JSON or serialized object)
     */
    private Object errorAnalysis;

    /**
     * Current incident status
     */
    @Indexed
    private ComplianceIncidentStatus status;

    /**
     * Whether this incident requires regulatory reporting
     */
    @Indexed
    private Boolean isRegulatoryReportable;

    /**
     * Incident creation timestamp
     */
    @Indexed
    private LocalDateTime createdAt;

    /**
     * SLA due date for resolution/review
     */
    @Indexed
    private LocalDateTime dueDate;

    /**
     * When incident was resolved
     */
    private LocalDateTime resolvedAt;

    /**
     * Who resolved the incident
     */
    private String resolvedBy;

    /**
     * Resolution description and actions taken
     */
    private String resolution;

    /**
     * Whether incident was escalated to executive/regulatory level
     */
    private Boolean wasEscalated;

    /**
     * Escalation details
     */
    private String escalationDetails;

    /**
     * Regulatory filing reference (if reported to FinCEN, IRS, OFAC, etc.)
     */
    private String regulatoryFilingReference;

    /**
     * When regulatory report was filed
     */
    private LocalDateTime regulatoryFiledAt;

    /**
     * Additional notes and investigation details
     */
    private String notes;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Last updated by user
     */
    private String updatedBy;

    /**
     * Check if incident is overdue based on SLA
     */
    public boolean isOverdue() {
        if (dueDate == null) return false;
        if (status == ComplianceIncidentStatus.RESOLVED ||
            status == ComplianceIncidentStatus.CLOSED ||
            status == ComplianceIncidentStatus.CANCELLED) {
            return false;
        }
        return LocalDateTime.now().isAfter(dueDate);
    }

    /**
     * Check if incident is critical and requires immediate action
     */
    public boolean isCritical() {
        return "CRITICAL_REGULATORY".equals(severity) ||
               "HIGH_REGULATORY".equals(severity) ||
               Boolean.TRUE.equals(isRegulatoryReportable);
    }

    /**
     * Get hours remaining until SLA due date
     */
    public long getHoursUntilDue() {
        if (dueDate == null) return Long.MAX_VALUE;
        return java.time.Duration.between(LocalDateTime.now(), dueDate).toHours();
    }
}
