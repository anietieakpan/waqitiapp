package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log for compliance-related events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAuditLog {
    
    private UUID id;
    private String userId;
    private String resourceId; // ID of the resource being audited
    private String complianceFramework; // GDPR, PCI-DSS, SOX, etc.
    private String regulationRule;
    private String eventType;
    private String description;
    private ComplianceLevel level;
    private ComplianceStatus status;
    private String violationDetails;
    private String remediationAction;
    private String approverUserId;
    private String documentReference;
    private LocalDateTime timestamp;
    private LocalDateTime dueDate;
    private Map<String, Object> metadata;
    private Map<String, Object> eventData; // Event-specific data
    
    /**
     * Compliance levels
     */
    public enum ComplianceLevel {
        INFO,
        WARNING,
        VIOLATION,
        CRITICAL_VIOLATION
    }
    
    /**
     * Compliance status
     */
    public enum ComplianceStatus {
        COMPLIANT,
        NON_COMPLIANT,
        PENDING_REVIEW,
        REMEDIATED,
        EXCEPTION_GRANTED
    }
    
    /**
     * Create compliance audit log
     */
    public static ComplianceAuditLog create(String userId, String complianceFramework, 
                                          String eventType, ComplianceLevel level) {
        return ComplianceAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .complianceFramework(complianceFramework)
                .eventType(eventType)
                .level(level)
                .status(ComplianceStatus.PENDING_REVIEW)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create GDPR audit log
     */
    public static ComplianceAuditLog createGDPR(String userId, String eventType, String description) {
        return create(userId, "GDPR", eventType, ComplianceLevel.INFO)
                .withDescription(description);
    }
    
    /**
     * Create PCI-DSS audit log
     */
    public static ComplianceAuditLog createPCIDSS(String userId, String eventType, String description) {
        return create(userId, "PCI-DSS", eventType, ComplianceLevel.WARNING)
                .withDescription(description);
    }
    
    /**
     * Create SOX audit log
     */
    public static ComplianceAuditLog createSOX(String userId, String eventType, String description) {
        return create(userId, "SOX", eventType, ComplianceLevel.INFO)
                .withDescription(description);
    }
    
    /**
     * Add description
     */
    public ComplianceAuditLog withDescription(String description) {
        this.description = description;
        return this;
    }
    
    /**
     * Add violation details
     */
    public ComplianceAuditLog withViolation(String violationDetails, String remediationAction) {
        this.violationDetails = violationDetails;
        this.remediationAction = remediationAction;
        this.status = ComplianceStatus.NON_COMPLIANT;
        return this;
    }
    
    /**
     * Add approval details
     */
    public ComplianceAuditLog withApproval(String approverUserId, String documentReference) {
        this.approverUserId = approverUserId;
        this.documentReference = documentReference;
        return this;
    }
    
    /**
     * Check if this represents a compliance violation
     */
    public boolean isViolation() {
        return level == ComplianceLevel.VIOLATION || 
               level == ComplianceLevel.CRITICAL_VIOLATION ||
               status == ComplianceStatus.NON_COMPLIANT;
    }
    
    /**
     * Get regulation being audited
     */
    public String getRegulation() {
        return complianceFramework;
    }
    
    /**
     * Get additional details about the compliance event
     */
    public Map<String, Object> getDetails() {
        return metadata;
    }
    
    /**
     * Get requirement (alias for regulationRule)
     */
    public String getRequirement() {
        return regulationRule != null ? regulationRule : "";
    }
    
    /**
     * Get status as string
     */
    public String getStatus() {
        return status != null ? status.name() : ComplianceStatus.PENDING_REVIEW.name();
    }
    
    /**
     * Get findings (combination of violation details and remediation action)
     */
    public String getFindings() {
        StringBuilder findings = new StringBuilder();
        if (violationDetails != null) {
            findings.append("Violation: ").append(violationDetails);
        }
        if (remediationAction != null) {
            if (findings.length() > 0) findings.append("; ");
            findings.append("Remediation: ").append(remediationAction);
        }
        return findings.length() > 0 ? findings.toString() : "No findings";
    }
}