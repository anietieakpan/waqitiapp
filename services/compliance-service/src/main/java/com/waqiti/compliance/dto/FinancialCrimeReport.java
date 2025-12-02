package com.waqiti.compliance.dto;

import com.waqiti.compliance.enums.CrimeSeverity;
import com.waqiti.compliance.enums.CrimeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Financial Crime Report DTO
 *
 * Data Transfer Object for comprehensive financial crime reporting
 * used for API responses, internal reporting, and regulatory submissions.
 *
 * Used for:
 * - REST API responses
 * - Internal reporting dashboards
 * - Regulatory report generation
 * - Executive summaries
 *
 * Compliance: Comprehensive crime reporting
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCrimeReport {

    /**
     * Crime case ID
     */
    private String caseId;

    /**
     * User ID involved
     */
    private String userId;

    /**
     * Crime type
     */
    private CrimeType crimeType;

    /**
     * Crime severity
     */
    private CrimeSeverity severity;

    /**
     * Case status
     */
    private String status;

    /**
     * Case description
     */
    private String description;

    /**
     * Case details (flexible JSON)
     */
    private Map<String, Object> details;

    /**
     * Case created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Case created by (user ID)
     */
    private String createdBy;

    /**
     * Case last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Case resolved timestamp
     */
    private LocalDateTime resolvedAt;

    /**
     * Case closed timestamp
     */
    private LocalDateTime closedAt;

    /**
     * Assigned investigator
     */
    private String assignedTo;

    /**
     * Account frozen flag
     */
    @Builder.Default
    private boolean accountFrozen = false;

    /**
     * User suspended flag
     */
    @Builder.Default
    private boolean userSuspended = false;

    /**
     * SAR required flag
     */
    @Builder.Default
    private boolean sarRequired = false;

    /**
     * SAR filed flag
     */
    @Builder.Default
    private boolean sarFiled = false;

    /**
     * SAR reference number
     */
    private String sarReferenceNumber;

    /**
     * SAR filed timestamp
     */
    private LocalDateTime sarFiledAt;

    /**
     * Law enforcement notification required flag
     */
    @Builder.Default
    private boolean lawEnforcementNotificationRequired = false;

    /**
     * Law enforcement notified flag
     */
    @Builder.Default
    private boolean lawEnforcementNotified = false;

    /**
     * Law enforcement notification timestamp
     */
    private LocalDateTime lawEnforcementNotifiedAt;

    /**
     * List of agencies notified
     */
    @Builder.Default
    private List<String> agenciesNotified = new ArrayList<>();

    /**
     * Evidence preserved flag
     */
    @Builder.Default
    private boolean evidencePreserved = false;

    /**
     * Follow-up required flag
     */
    @Builder.Default
    private boolean followUpRequired = false;

    /**
     * Follow-up notes
     */
    private String followUpNotes;

    /**
     * Resolution notes
     */
    private String resolutionNotes;

    /**
     * Estimated financial impact (USD)
     */
    private String estimatedImpact;

    /**
     * Number of transactions involved
     */
    private Integer transactionCount;

    /**
     * Total amount involved (USD)
     */
    private String totalAmount;

    /**
     * Currency code
     */
    private String currency;

    /**
     * List of transaction IDs
     */
    @Builder.Default
    private List<String> transactionIds = new ArrayList<>();

    /**
     * List of linked investigations
     */
    @Builder.Default
    private List<String> linkedInvestigations = new ArrayList<>();

    /**
     * Risk score (0-100)
     */
    private Integer riskScore;

    /**
     * Priority (P0, P1, P2, P3)
     */
    private String priority;

    /**
     * Report version for API versioning
     */
    @Builder.Default
    private String reportVersion = "1.0";

    /**
     * Report generated timestamp
     */
    @Builder.Default
    private LocalDateTime reportGeneratedAt = LocalDateTime.now();

    /**
     * Check if case is critical
     *
     * @return true if critical
     */
    public boolean isCritical() {
        return severity == CrimeSeverity.CRITICAL || "P0".equals(priority);
    }

    /**
     * Check if case is closed
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return "CLOSED".equals(status);
    }

    /**
     * Check if case is active
     *
     * @return true if active (not closed)
     */
    public boolean isActive() {
        return !"CLOSED".equals(status);
    }

    /**
     * Check if regulatory reporting is complete
     *
     * @return true if all required reporting done
     */
    public boolean isRegulatoryReportingComplete() {
        boolean sarComplete = !sarRequired || sarFiled;
        boolean leComplete = !lawEnforcementNotificationRequired || lawEnforcementNotified;
        return sarComplete && leComplete;
    }

    /**
     * Add agency to notified list
     *
     * @param agency agency name
     */
    public void addNotifiedAgency(String agency) {
        if (agenciesNotified == null) {
            agenciesNotified = new ArrayList<>();
        }
        if (!agenciesNotified.contains(agency)) {
            agenciesNotified.add(agency);
        }
    }

    /**
     * Add linked investigation
     *
     * @param investigationId investigation ID
     */
    public void addLinkedInvestigation(String investigationId) {
        if (linkedInvestigations == null) {
            linkedInvestigations = new ArrayList<>();
        }
        if (!linkedInvestigations.contains(investigationId)) {
            linkedInvestigations.add(investigationId);
        }
    }
}
