package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud enforcement action with regulatory compliance tracking
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudEnforcementAction {
    
    private String enforcementId;
    private String violationId;
    private String transactionId;
    private String userId;
    private String accountId;
    private Integer violationCount; // PRODUCTION FIX: Number of violations associated with this action
    
    // Enforcement details
    private EnforcementActionType actionType;
    private EnforcementSeverity severity;
    private String description;
    private String reason; // PRODUCTION FIX: Alias for justification for builder
    private String legalBasis;
    private String justification;
    
    // Execution tracking
    private EnforcementStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime executedAt;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private String executedBy;
    private String executionResult;
    
    // Legal and compliance
    private Boolean isRegulatoryAction;
    private List<String> applicableRegulations;
    private String complianceFramework;
    private String authorityNotified;
    private LocalDateTime authorityNotificationDate;
    private Boolean requiresCustomerNotification;
    
    // Due process requirements
    private Boolean dueProcessCompleted;
    private String dueProcessDocumentation;
    private LocalDateTime customerNotificationSent;
    private String customerNotificationMethod;
    private String appealRights;
    private LocalDateTime appealDeadline;
    private Boolean mandatory; // PRODUCTION FIX: Whether this action is mandatory
    
    // Impact assessment
    private String businessImpact;
    private String customerImpact;
    private String reputationalImpact;
    private List<String> affectedServices;
    private String mitigationMeasures;
    
    // Financial implications
    private String financialPenalty;
    private String assetFreezingAmount;
    private String compensationRequired;
    private String costRecoveryMethod;
    
    // Documentation and evidence
    private List<String> supportingDocuments;
    private String evidenceReference;
    private String investigationReport;
    private String legalOpinionRequired;
    
    // Monitoring and review
    private Boolean requiresOngoingMonitoring;
    private String monitoringFrequency;
    private LocalDateTime nextReviewDate;
    private String reviewCriteria;
    private List<String> performanceIndicators;
    
    // Coordination and dependencies
    private List<String> coordinatedActions;
    private String enforcementGroup;
    private Boolean requiresMultiAgencyCoordination;
    private List<String> involvedAgencies;
    
    // Appeal and modification tracking
    private Boolean appealFiled;
    private LocalDateTime appealFiledDate;
    private String appealStatus;
    private String appealOutcome;
    private Boolean actionModified;
    private String modificationReason;
    
    // Additional metadata
    private Map<String, Object> enforcementData;
    private List<String> tags;
    private String notes;
    
    /**
     * Types of enforcement actions
     */
    public enum EnforcementActionType {
        // PRODUCTION FIX: Basic actions for ComprehensiveFraudBlacklistService
        ALLOW,
        BLOCK,
        REVIEW,
        CHALLENGE,

        // Account actions
        ACCOUNT_SUSPENSION,
        ACCOUNT_CLOSURE,
        ACCOUNT_RESTRICTION,
        ACCOUNT_MONITORING,

        // Financial actions
        ASSET_FREEZING,
        FUND_SEIZURE,
        TRANSACTION_BLOCKING,
        PAYMENT_REVERSAL,
        
        // Regulatory actions
        REGULATORY_REPORTING,
        SUSPICIOUS_ACTIVITY_REPORT,
        CURRENCY_TRANSACTION_REPORT,
        LAW_ENFORCEMENT_REFERRAL,
        
        // Administrative actions
        WARNING_NOTICE,
        COMPLIANCE_ORDER,
        CEASE_AND_DESIST,
        ADMINISTRATIVE_PENALTY,
        
        // Legal actions
        CIVIL_ACTION,
        CRIMINAL_REFERRAL,
        INJUNCTIVE_RELIEF,
        ASSET_RECOVERY,
        
        // Operational actions
        SYSTEM_ACCESS_REVOCATION,
        SERVICE_TERMINATION,
        MERCHANT_BLACKLISTING,
        IP_BLOCKING,
        
        // Preventive actions
        ENHANCED_DUE_DILIGENCE,
        TRANSACTION_LIMITS,
        GEOGRAPHIC_RESTRICTIONS,
        TIME_BASED_RESTRICTIONS
    }
    
    /**
     * Severity levels for enforcement actions
     */
    public enum EnforcementSeverity {
        ADVISORY,        // Warning/guidance only
        ADMINISTRATIVE,  // Administrative measures
        CORRECTIVE,      // Corrective actions required
        PUNITIVE,        // Punitive measures
        SEVERE,          // Severe sanctions
        CRIMINAL         // Criminal enforcement
    }
    
    /**
     * Status of enforcement action
     */
    public enum EnforcementStatus {
        PROPOSED,
        UNDER_REVIEW,
        APPROVED,
        SCHEDULED,
        IN_PROGRESS,
        EXECUTED,
        SUSPENDED,
        APPEALED,
        MODIFIED,
        REVERSED,
        EXPIRED
    }
    
    /**
     * Check if enforcement action is currently active
     */
    public boolean isCurrentlyActive() {
        if (status != EnforcementStatus.EXECUTED) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean effectiveFromPassed = effectiveFrom == null || !now.isBefore(effectiveFrom);
        boolean effectiveUntilNotReached = effectiveUntil == null || now.isBefore(effectiveUntil);
        
        return effectiveFromPassed && effectiveUntilNotReached;
    }
    
    /**
     * Calculate enforcement severity score
     */
    public double calculateSeverityScore() {
        double baseScore;
        
        switch (severity) {
            case CRIMINAL:
                baseScore = 1.0;
                break;
            case SEVERE:
                baseScore = 0.9;
                break;
            case PUNITIVE:
                baseScore = 0.7;
                break;
            case CORRECTIVE:
                baseScore = 0.5;
                break;
            case ADMINISTRATIVE:
                baseScore = 0.3;
                break;
            case ADVISORY:
                baseScore = 0.1;
                break;
            default:
                baseScore = 0.0;
        }
        
        // Adjust for regulatory nature
        if (isRegulatoryAction != null && isRegulatoryAction) {
            baseScore *= 1.2;
        }
        
        // Adjust for multi-agency coordination
        if (requiresMultiAgencyCoordination != null && requiresMultiAgencyCoordination) {
            baseScore *= 1.3;
        }
        
        return Math.min(1.0, baseScore);
    }
    
    /**
     * Check if action requires immediate execution
     */
    public boolean requiresImmediateExecution() {
        return severity == EnforcementSeverity.SEVERE ||
               severity == EnforcementSeverity.CRIMINAL ||
               (actionType == EnforcementActionType.ASSET_FREEZING) ||
               (actionType == EnforcementActionType.FUND_SEIZURE) ||
               (actionType == EnforcementActionType.LAW_ENFORCEMENT_REFERRAL);
    }
    
    /**
     * Get required due process steps
     */
    public List<String> getRequiredDueProcessSteps() {
        switch (severity) {
            case CRIMINAL:
            case SEVERE:
                return List.of(
                    "LEGAL_REVIEW_REQUIRED",
                    "EVIDENCE_VALIDATION",
                    "CUSTOMER_NOTIFICATION",
                    "APPEAL_RIGHTS_NOTICE",
                    "MANAGEMENT_APPROVAL",
                    "REGULATORY_CLEARANCE"
                );
                
            case PUNITIVE:
                return List.of(
                    "LEGAL_REVIEW_REQUIRED",
                    "CUSTOMER_NOTIFICATION",
                    "APPEAL_RIGHTS_NOTICE",
                    "MANAGEMENT_APPROVAL"
                );
                
            case CORRECTIVE:
                return List.of(
                    "CUSTOMER_NOTIFICATION",
                    "APPEAL_RIGHTS_NOTICE",
                    "SUPERVISOR_APPROVAL"
                );
                
            case ADMINISTRATIVE:
                return List.of(
                    "CUSTOMER_NOTIFICATION",
                    "SUPERVISOR_APPROVAL"
                );
                
            case ADVISORY:
                return List.of("CUSTOMER_NOTIFICATION");
                
            default:
                return List.of();
        }
    }
    
    /**
     * Check if all due process requirements are met
     */
    public boolean isDueProcessComplete() {
        if (dueProcessCompleted != null && dueProcessCompleted) {
            return true;
        }
        
        List<String> requiredSteps = getRequiredDueProcessSteps();
        
        // Check customer notification requirement
        if (requiredSteps.contains("CUSTOMER_NOTIFICATION") && 
            (customerNotificationSent == null || requiresCustomerNotification == Boolean.FALSE)) {
            return false;
        }
        
        // Check legal review requirement
        if (requiredSteps.contains("LEGAL_REVIEW_REQUIRED") && 
            legalOpinionRequired != null && legalOpinionRequired.isEmpty()) {
            return false;
        }
        
        // Check regulatory clearance requirement
        if (requiredSteps.contains("REGULATORY_CLEARANCE") && 
            (authorityNotificationDate == null && isRegulatoryAction == Boolean.TRUE)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Generate enforcement action summary
     */
    public String generateEnforcementSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("=== FRAUD ENFORCEMENT ACTION ===\n");
        summary.append("Action ID: ").append(enforcementId).append("\n");
        summary.append("Type: ").append(actionType).append("\n");
        summary.append("Severity: ").append(severity).append("\n");
        summary.append("Status: ").append(status).append("\n");
        
        if (legalBasis != null) {
            summary.append("Legal Basis: ").append(legalBasis).append("\n");
        }
        
        summary.append("\n=== EXECUTION DETAILS ===\n");
        if (effectiveFrom != null) {
            summary.append("Effective From: ").append(effectiveFrom).append("\n");
        }
        if (effectiveUntil != null) {
            summary.append("Effective Until: ").append(effectiveUntil).append("\n");
        }
        summary.append("Currently Active: ").append(isCurrentlyActive() ? "YES" : "NO").append("\n");
        
        if (isRegulatoryAction != null && isRegulatoryAction) {
            summary.append("\n=== REGULATORY COMPLIANCE ===\n");
            summary.append("Regulatory Action: YES\n");
            if (applicableRegulations != null && !applicableRegulations.isEmpty()) {
                summary.append("Applicable Regulations: ").append(String.join(", ", applicableRegulations)).append("\n");
            }
            if (authorityNotified != null) {
                summary.append("Authority Notified: ").append(authorityNotified).append("\n");
            }
        }
        
        summary.append("\n=== DUE PROCESS ===\n");
        summary.append("Due Process Complete: ").append(isDueProcessComplete() ? "YES" : "NO").append("\n");
        if (customerNotificationSent != null) {
            summary.append("Customer Notified: ").append(customerNotificationSent).append("\n");
        }
        if (appealDeadline != null) {
            summary.append("Appeal Deadline: ").append(appealDeadline).append("\n");
        }
        
        if (appealFiled != null && appealFiled) {
            summary.append("\n=== APPEAL STATUS ===\n");
            summary.append("Appeal Filed: ").append(appealFiledDate).append("\n");
            summary.append("Appeal Status: ").append(appealStatus).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Calculate days until action expires
     */
    public Long getDaysUntilExpiration() {
        if (effectiveUntil == null) {
            return null; // Permanent action
        }
        
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), effectiveUntil);
    }
    
    /**
     * Check if action is expiring soon
     */
    public boolean isExpiringSoon() {
        Long daysUntilExpiration = getDaysUntilExpiration();
        return daysUntilExpiration != null && daysUntilExpiration <= 30;
    }
    
    /**
     * Get compliance requirements checklist
     */
    public Map<String, Boolean> getComplianceChecklist() {
        Map<String, Boolean> checklist = Map.of(
            "legalBasisDocumented", legalBasis != null && !legalBasis.isEmpty(),
            "dueProcessCompleted", isDueProcessComplete(),
            "customerNotificationSent", customerNotificationSent != null,
            "appealRightsProvided", appealRights != null && !appealRights.isEmpty(),
            "evidenceDocumented", evidenceReference != null && !evidenceReference.isEmpty(),
            "regulatoryNotificationCompleted", !Boolean.TRUE.equals(isRegulatoryAction) || authorityNotificationDate != null,
            "ongoingMonitoringConfigured", !Boolean.TRUE.equals(requiresOngoingMonitoring) || monitoringFrequency != null,
            "reviewScheduleSet", nextReviewDate != null
        );
        
        return checklist;
    }
    
    /**
     * Update enforcement action status
     */
    public void updateStatus(EnforcementStatus newStatus, String executedBy, String result) {
        this.status = newStatus;
        
        if (newStatus == EnforcementStatus.EXECUTED) {
            this.executedAt = LocalDateTime.now();
            this.executedBy = executedBy;
            this.executionResult = result;
            
            if (effectiveFrom == null) {
                this.effectiveFrom = LocalDateTime.now();
            }
        }
    }
}