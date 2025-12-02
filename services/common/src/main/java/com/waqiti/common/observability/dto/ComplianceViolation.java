package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waqiti.common.enums.RiskLevel;
import com.waqiti.common.enums.ViolationSeverity;
import com.waqiti.common.enums.BusinessImpact;
import com.waqiti.common.enums.BusinessImpactLevel;
import com.waqiti.common.observability.dto.ReportingRequirements;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

/**
 * Comprehensive compliance violation tracking and management
 * Handles regulatory, security, and business compliance violations with detailed context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceViolation {
    
    private String violationId;
    private String violationTitle;
    private String description;
    private ComplianceViolationType type;
    private ViolationSeverity severity;
    private ViolationStatus status;
    private LocalDateTime detectedAt;
    private LocalDateTime updatedAt;
    
    // Compliance context
    private String regulatoryFramework; // GDPR, PCI-DSS, SOX, etc.
    private String complianceStandard;
    private String requirementId;
    private String requirementDescription;
    private ComplianceDomain domain;
    
    // Violation details
    private String affectedSystem;
    private String affectedComponent;
    private List<String> affectedResources;
    private String dataClassification;
    private long affectedRecords;
    private List<String> affectedUsers;
    
    // Business impact
    private BusinessImpact businessImpact;
    private double riskScore;
    private RiskLevel riskLevel;
    private String businessJustification;
    private CustomerImpact customerImpact;
    
    // Root cause analysis
    private String rootCause;
    private List<String> contributingFactors;
    private String technicalDetails;
    private Map<String, Object> evidenceData;
    private List<String> relatedIncidents;
    private RootCauseAnalysis rootCauseAnalysis;
    private IncidentTimeline incidentTimeline;
    
    // Resolution tracking
    private ResolutionPlan resolutionPlan;
    private List<RemediationAction> remediationActions;
    private RemediationPlan remediationPlan;
    private RemediationStatus remediationStatus;
    private LocalDateTime targetResolutionDate;
    private LocalDateTime actualResolutionDate;
    private String resolutionSummary;
    private boolean isResolved;
    
    // Compliance reporting
    private ReportingRequirements reportingRequirements;
    private List<String> notificationsSent;
    private List<ComplianceReport> complianceReports;
    private AuditTrail auditTrail;
    
    // Financial and legal impact
    private FinancialAssessment financialAssessment;
    private LegalAssessment legalAssessment;
    private List<PotentialPenalty> potentialPenalties;
    private InsuranceClaim insuranceClaim;
    
    // Stakeholder management
    private List<String> assignedTeams;
    private String primaryOwner;
    private String complianceOfficer;
    private EscalationMatrix escalationMatrix;
    private List<StakeholderNotification> stakeholderNotifications;
    
    // Prevention and controls
    private List<String> failedControls;
    private List<String> recommendedControls;
    private PreventionMeasures preventionMeasures;
    private ControlEffectiveness controlEffectiveness;
    
    /**
     * Calculate violation risk score based on multiple factors (immutable calculation)
     */
    public double calculateRiskScore() {
        if (riskScore > 0) {
            return riskScore;
        }
        
        try {
            double score = 0.0;
            
            // Severity impact (40% weight)
            score += getSeverityScore() * 0.4;
            
            // Business impact (30% weight)
            if (businessImpact != null) {
                score += businessImpact.getImpactScore() * 0.3;
            }
            
            // Affected records scale (20% weight)
            score += getDataVolumeScore() * 0.2;
            
            // Regulatory framework criticality (10% weight)
            score += getRegulatoryScore() * 0.1;
            
            // Return calculated score without modifying object state
            return Math.max(0.0, Math.min(100.0, score));
            
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Determine if violation requires immediate escalation
     */
    public boolean requiresImmediateEscalation() {
        return severity == ViolationSeverity.CRITICAL ||
               type == ComplianceViolationType.DATA_BREACH ||
               affectedRecords > 10000 ||
               (reportingRequirements != null && reportingRequirements.isImmediateReportingRequired()) ||
               calculateRiskScore() > 80.0;
    }
    
    /**
     * Get days until compliance deadline
     */
    public long getDaysUntilDeadline() {
        if (targetResolutionDate == null) {
            return -1;
        }
        
        return Duration.between(LocalDateTime.now(), targetResolutionDate).toDays();
    }
    
    /**
     * Check if violation is overdue
     */
    public boolean isOverdue() {
        if (targetResolutionDate == null || isResolved) {
            return false;
        }
        
        return LocalDateTime.now().isAfter(targetResolutionDate);
    }
    
    /**
     * Get violation age in hours
     */
    public long getViolationAgeHours() {
        return Duration.between(detectedAt, LocalDateTime.now()).toHours();
    }
    
    /**
     * Get comprehensive violation summary for reporting
     */
    public String getViolationSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Violation: %s [%s]\n", violationTitle, violationId));
        summary.append(String.format("Type: %s | Severity: %s\n", type.getDisplayName(), severity.getDisplayName()));
        summary.append(String.format("Framework: %s | Standard: %s\n", regulatoryFramework, complianceStandard));
        summary.append(String.format("Detected: %s | Age: %d hours\n", 
            detectedAt.toString(), getViolationAgeHours()));
        
        if (affectedRecords > 0) {
            summary.append(String.format("Affected Records: %,d\n", affectedRecords));
        }
        
        if (businessImpact != null) {
            summary.append(String.format("Business Impact: %s\n", businessImpact.getImpactLevel()));
        }
        
        if (targetResolutionDate != null) {
            long daysLeft = getDaysUntilDeadline();
            if (daysLeft >= 0) {
                summary.append(String.format("Deadline: %d days remaining\n", daysLeft));
            } else {
                summary.append(String.format("OVERDUE by %d days\n", Math.abs(daysLeft)));
            }
        }
        
        if (rootCause != null && !rootCause.isEmpty()) {
            summary.append(String.format("Root Cause: %s\n", rootCause));
        }
        
        return summary.toString().trim();
    }
    
    /**
     * Get required regulatory notifications
     */
    public List<String> getRequiredNotifications() {
        if (reportingRequirements == null) {
            return List.of();
        }
        
        return reportingRequirements.getRequiredNotifications();
    }
    
    /**
     * Check if notification deadline is approaching
     */
    public boolean isNotificationDeadlineApproaching() {
        if (reportingRequirements == null || reportingRequirements.getNotificationDeadline() == null) {
            return false;
        }
        
        LocalDateTime deadline = reportingRequirements.getNotificationDeadline();
        Duration timeUntilDeadline = Duration.between(LocalDateTime.now(), deadline);
        
        return timeUntilDeadline.toHours() <= 24; // Within 24 hours
    }
    
    /**
     * Get compliance remediation priority
     */
    public RemediationPriority getRemediationPriority() {
        double riskScore = calculateRiskScore();
        
        if (severity == ViolationSeverity.CRITICAL || riskScore > 90) {
            return RemediationPriority.EMERGENCY;
        } else if (severity == ViolationSeverity.HIGH || riskScore > 70) {
            return RemediationPriority.HIGH;
        } else if (severity == ViolationSeverity.MEDIUM || riskScore > 50) {
            return RemediationPriority.MEDIUM;
        } else {
            return RemediationPriority.LOW;
        }
    }
    
    /**
     * Generate compliance action plan
     */
    public List<String> generateActionPlan() {
        List<String> actionPlan = new java.util.ArrayList<>();
        
        // Immediate actions
        if (requiresImmediateEscalation()) {
            actionPlan.add("IMMEDIATE: Escalate to compliance officer and legal team");
            actionPlan.add("IMMEDIATE: Contain violation to prevent further exposure");
        }
        
        // Notification requirements
        if (isNotificationDeadlineApproaching()) {
            actionPlan.add("URGENT: Prepare and submit regulatory notifications");
        }
        
        // Investigation actions
        if (rootCause == null || rootCause.isEmpty()) {
            actionPlan.add("Conduct root cause analysis");
            actionPlan.add("Document evidence and maintain audit trail");
        }
        
        // Remediation actions
        if (remediationActions != null && !remediationActions.isEmpty()) {
            actionPlan.add("Execute planned remediation actions");
            actionPlan.add("Monitor remediation effectiveness");
        }
        
        // Prevention actions
        if (recommendedControls != null && !recommendedControls.isEmpty()) {
            actionPlan.add("Implement recommended preventive controls");
            actionPlan.add("Update compliance monitoring procedures");
        }
        
        // Financial and legal assessment
        if (potentialPenalties != null && !potentialPenalties.isEmpty()) {
            actionPlan.add("Complete financial impact assessment");
            actionPlan.add("Review insurance coverage and claims process");
        }
        
        return actionPlan;
    }
    
    /**
     * Check if violation affects customer data
     */
    public boolean affectsCustomerData() {
        return type == ComplianceViolationType.DATA_BREACH ||
               type == ComplianceViolationType.PRIVACY_VIOLATION ||
               (dataClassification != null && dataClassification.contains("PII")) ||
               affectedRecords > 0;
    }
    
    /**
     * Get estimated resolution effort
     */
    public String getEstimatedResolutionEffort() {
        if (resolutionPlan != null && resolutionPlan.getEstimatedEffort() != null) {
            return resolutionPlan.getEstimatedEffort();
        }
        
        // Estimate based on severity and complexity
        return switch (severity) {
            case CRITICAL -> "High - Multiple teams, 2-4 weeks";
            case HIGH -> "Medium - Dedicated team, 1-2 weeks";
            case MEDIUM -> "Medium - Part-time effort, 3-7 days";
            case LOW -> "Low - Individual contributor, 1-3 days";
        };
    }
    
    private double getSeverityScore() {
        return switch (severity) {
            case CRITICAL -> 100.0;
            case HIGH -> 75.0;
            case MEDIUM -> 50.0;
            case LOW -> 25.0;
        };
    }
    
    private double getDataVolumeScore() {
        if (affectedRecords == 0) return 0.0;
        if (affectedRecords > 100000) return 100.0;
        if (affectedRecords > 10000) return 75.0;
        if (affectedRecords > 1000) return 50.0;
        if (affectedRecords > 100) return 25.0;
        return 10.0;
    }
    
    private double getRegulatoryScore() {
        if (regulatoryFramework == null) return 0.0;
        
        return switch (regulatoryFramework.toUpperCase()) {
            case "GDPR", "CCPA", "SOX" -> 100.0;
            case "PCI-DSS", "HIPAA" -> 90.0;
            case "ISO27001", "SOC2" -> 70.0;
            default -> 50.0;
        };
    }
    
    /**
     * Create a critical data breach violation
     */
    public static ComplianceViolation createDataBreach(String title, long affectedRecords, String framework) {
        return ComplianceViolation.builder()
            .violationId(generateViolationId())
            .violationTitle(title)
            .type(ComplianceViolationType.DATA_BREACH)
            .severity(ViolationSeverity.CRITICAL)
            .status(ViolationStatus.ACTIVE)
            .detectedAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .regulatoryFramework(framework)
            .affectedRecords(affectedRecords)
            .domain(ComplianceDomain.DATA_PROTECTION)
            .riskLevel(RiskLevel.CRITICAL)
            .isResolved(false)
            .businessImpact(BusinessImpact.builder()
                .impactLevel(BusinessImpactLevel.CRITICAL)
                .impactScore(95.0)
                .build())
            .reportingRequirements(ReportingRequirements.builder()
                .isImmediateReportingRequired(true)
                .notificationDeadline(LocalDateTime.now().plusHours(72))
                .requiredNotifications(List.of("Regulatory Authority", "Data Protection Officer", "Affected Customers"))
                .build())
            .build();
    }
    
    /**
     * Generate cryptographically secure violation ID
     * 
     * SECURITY FIX: Replaced Math.random() with SecureRandom for unpredictable IDs
     * This prevents attackers from predicting violation IDs and potentially manipulating audit trails
     */
    private static String generateViolationId() {
        SecureRandom secureRandom = new SecureRandom();
        int randomSuffix = secureRandom.nextInt(10000); // Increased range for better uniqueness
        
        // Add thread ID for additional uniqueness in multi-threaded environments
        long threadId = Thread.currentThread().getId();
        
        return String.format("CV-%d-%04d-T%d", System.currentTimeMillis(), randomSuffix, threadId);
    }
    
    // Removed custom builder - using Lombok @Builder instead
    /* Commenting out custom builder to avoid conflicts with Lombok
    public static class ComplianceViolationBuilder {
        private String violationId;
        private String violationTitle;
        private String description;
        private ComplianceViolationType type;
        private ViolationSeverity severity;
        private ViolationStatus status;
        private LocalDateTime detectedAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
        private String regulatoryFramework;
        private String complianceStandard;
        private String requirementId;
        private String requirementDescription;
        private ComplianceDomain domain;
        private String affectedSystem;
        private String affectedComponent;
        private List<String> affectedResources;
        private String dataClassification;
        private long affectedRecords;
        private List<String> affectedUsers;
        private BusinessImpact businessImpact;
        private double riskScore;
        private RiskLevel riskLevel;
        private String businessJustification;
        private CustomerImpact customerImpact;
        private String rootCause;
        private List<String> contributingFactors;
        private String technicalDetails;
        private Map<String, Object> evidenceData;
        private List<String> relatedIncidents;
        private RootCauseAnalysis rootCauseAnalysis;
        private IncidentTimeline incidentTimeline;
        private ResolutionPlan resolutionPlan;
        private List<RemediationAction> remediationActions;
        private RemediationPlan remediationPlan;
        private RemediationStatus remediationStatus;
        private LocalDateTime targetResolutionDate;
        private LocalDateTime actualResolutionDate;
        private String resolutionSummary;
        private boolean isResolved;
        private ReportingRequirements reportingRequirements;
        private List<String> notificationsSent;
        private List<ComplianceReport> complianceReports;
        private AuditTrail auditTrail;
        private FinancialAssessment financialAssessment;
        private LegalAssessment legalAssessment;
        private List<PotentialPenalty> potentialPenalties;
        private InsuranceClaim insuranceClaim;
        private List<String> assignedTeams;
        private String primaryOwner;
        private String complianceOfficer;
        private EscalationMatrix escalationMatrix;
        private List<StakeholderNotification> stakeholderNotifications;
        private List<String> failedControls;
        private List<String> recommendedControls;
        private PreventionMeasures preventionMeasures;
        private ControlEffectiveness controlEffectiveness;
        
        public ComplianceViolationBuilder violationId(String violationId) { this.violationId = violationId; return this; }
        public ComplianceViolationBuilder violationTitle(String violationTitle) { this.violationTitle = violationTitle; return this; }
        public ComplianceViolationBuilder description(String description) { this.description = description; return this; }
        public ComplianceViolationBuilder type(ComplianceViolationType type) { this.type = type; return this; }
        public ComplianceViolationBuilder severity(ViolationSeverity severity) { this.severity = severity; return this; }
        public ComplianceViolationBuilder status(ViolationStatus status) { this.status = status; return this; }
        public ComplianceViolationBuilder detectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; return this; }
        public ComplianceViolationBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public ComplianceViolationBuilder regulatoryFramework(String regulatoryFramework) { this.regulatoryFramework = regulatoryFramework; return this; }
        public ComplianceViolationBuilder complianceStandard(String complianceStandard) { this.complianceStandard = complianceStandard; return this; }
        public ComplianceViolationBuilder requirementId(String requirementId) { this.requirementId = requirementId; return this; }
        public ComplianceViolationBuilder requirementDescription(String requirementDescription) { this.requirementDescription = requirementDescription; return this; }
        public ComplianceViolationBuilder domain(ComplianceDomain domain) { this.domain = domain; return this; }
        public ComplianceViolationBuilder affectedSystem(String affectedSystem) { this.affectedSystem = affectedSystem; return this; }
        public ComplianceViolationBuilder affectedComponent(String affectedComponent) { this.affectedComponent = affectedComponent; return this; }
        public ComplianceViolationBuilder affectedResources(List<String> affectedResources) { this.affectedResources = affectedResources; return this; }
        public ComplianceViolationBuilder dataClassification(String dataClassification) { this.dataClassification = dataClassification; return this; }
        public ComplianceViolationBuilder affectedRecords(long affectedRecords) { this.affectedRecords = affectedRecords; return this; }
        public ComplianceViolationBuilder affectedUsers(List<String> affectedUsers) { this.affectedUsers = affectedUsers; return this; }
        public ComplianceViolationBuilder businessImpact(BusinessImpact businessImpact) { this.businessImpact = businessImpact; return this; }
        public ComplianceViolationBuilder riskScore(double riskScore) { this.riskScore = riskScore; return this; }
        public ComplianceViolationBuilder riskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; return this; }
        public ComplianceViolationBuilder businessJustification(String businessJustification) { this.businessJustification = businessJustification; return this; }
        public ComplianceViolationBuilder customerImpact(CustomerImpact customerImpact) { this.customerImpact = customerImpact; return this; }
        public ComplianceViolationBuilder rootCause(String rootCause) { this.rootCause = rootCause; return this; }
        public ComplianceViolationBuilder contributingFactors(List<String> contributingFactors) { this.contributingFactors = contributingFactors; return this; }
        public ComplianceViolationBuilder technicalDetails(String technicalDetails) { this.technicalDetails = technicalDetails; return this; }
        public ComplianceViolationBuilder evidenceData(Map<String, Object> evidenceData) { this.evidenceData = evidenceData; return this; }
        public ComplianceViolationBuilder relatedIncidents(List<String> relatedIncidents) { this.relatedIncidents = relatedIncidents; return this; }
        public ComplianceViolationBuilder rootCauseAnalysis(RootCauseAnalysis rootCauseAnalysis) { this.rootCauseAnalysis = rootCauseAnalysis; return this; }
        public ComplianceViolationBuilder incidentTimeline(IncidentTimeline incidentTimeline) { this.incidentTimeline = incidentTimeline; return this; }
        public ComplianceViolationBuilder resolutionPlan(ResolutionPlan resolutionPlan) { this.resolutionPlan = resolutionPlan; return this; }
        public ComplianceViolationBuilder remediationActions(List<RemediationAction> remediationActions) { this.remediationActions = remediationActions; return this; }
        public ComplianceViolationBuilder remediationPlan(RemediationPlan remediationPlan) { this.remediationPlan = remediationPlan; return this; }
        public ComplianceViolationBuilder remediationStatus(RemediationStatus remediationStatus) { this.remediationStatus = remediationStatus; return this; }
        public ComplianceViolationBuilder targetResolutionDate(LocalDateTime targetResolutionDate) { this.targetResolutionDate = targetResolutionDate; return this; }
        public ComplianceViolationBuilder actualResolutionDate(LocalDateTime actualResolutionDate) { this.actualResolutionDate = actualResolutionDate; return this; }
        public ComplianceViolationBuilder resolutionSummary(String resolutionSummary) { this.resolutionSummary = resolutionSummary; return this; }
        public ComplianceViolationBuilder isResolved(boolean isResolved) { this.isResolved = isResolved; return this; }
        public ComplianceViolationBuilder reportingRequirements(ReportingRequirements reportingRequirements) { this.reportingRequirements = reportingRequirements; return this; }
        public ComplianceViolationBuilder notificationsSent(List<String> notificationsSent) { this.notificationsSent = notificationsSent; return this; }
        public ComplianceViolationBuilder complianceReports(List<ComplianceReport> complianceReports) { this.complianceReports = complianceReports; return this; }
        public ComplianceViolationBuilder auditTrail(AuditTrail auditTrail) { this.auditTrail = auditTrail; return this; }
        public ComplianceViolationBuilder financialAssessment(FinancialAssessment financialAssessment) { this.financialAssessment = financialAssessment; return this; }
        public ComplianceViolationBuilder legalAssessment(LegalAssessment legalAssessment) { this.legalAssessment = legalAssessment; return this; }
        public ComplianceViolationBuilder potentialPenalties(List<PotentialPenalty> potentialPenalties) { this.potentialPenalties = potentialPenalties; return this; }
        public ComplianceViolationBuilder insuranceClaim(InsuranceClaim insuranceClaim) { this.insuranceClaim = insuranceClaim; return this; }
        public ComplianceViolationBuilder assignedTeams(List<String> assignedTeams) { this.assignedTeams = assignedTeams; return this; }
        public ComplianceViolationBuilder primaryOwner(String primaryOwner) { this.primaryOwner = primaryOwner; return this; }
        public ComplianceViolationBuilder complianceOfficer(String complianceOfficer) { this.complianceOfficer = complianceOfficer; return this; }
        public ComplianceViolationBuilder escalationMatrix(EscalationMatrix escalationMatrix) { this.escalationMatrix = escalationMatrix; return this; }
        public ComplianceViolationBuilder stakeholderNotifications(List<StakeholderNotification> stakeholderNotifications) { this.stakeholderNotifications = stakeholderNotifications; return this; }
        public ComplianceViolationBuilder failedControls(List<String> failedControls) { this.failedControls = failedControls; return this; }
        public ComplianceViolationBuilder recommendedControls(List<String> recommendedControls) { this.recommendedControls = recommendedControls; return this; }
        public ComplianceViolationBuilder preventionMeasures(PreventionMeasures preventionMeasures) { this.preventionMeasures = preventionMeasures; return this; }
        public ComplianceViolationBuilder controlEffectiveness(ControlEffectiveness controlEffectiveness) { this.controlEffectiveness = controlEffectiveness; return this; }
        
        public ComplianceViolation build() {
            ComplianceViolation violation = new ComplianceViolation();
            violation.setViolationId(violationId);
            violation.setViolationTitle(violationTitle);
            violation.setDescription(description);
            violation.setType(type);
            violation.setSeverity(severity);
            violation.setStatus(status);
            violation.setDetectedAt(detectedAt);
            violation.setUpdatedAt(updatedAt);
            violation.setRegulatoryFramework(regulatoryFramework);
            violation.setComplianceStandard(complianceStandard);
            violation.setRequirementId(requirementId);
            violation.setRequirementDescription(requirementDescription);
            violation.setDomain(domain);
            violation.setAffectedSystem(affectedSystem);
            violation.setAffectedComponent(affectedComponent);
            violation.setAffectedResources(affectedResources);
            violation.setDataClassification(dataClassification);
            violation.setAffectedRecords(affectedRecords);
            violation.setAffectedUsers(affectedUsers);
            violation.setBusinessImpact(businessImpact);
            violation.setRiskScore(riskScore);
            violation.setRiskLevel(riskLevel);
            violation.setBusinessJustification(businessJustification);
            violation.setCustomerImpact(customerImpact);
            violation.setRootCause(rootCause);
            violation.setContributingFactors(contributingFactors);
            violation.setTechnicalDetails(technicalDetails);
            violation.setEvidenceData(evidenceData);
            violation.setRelatedIncidents(relatedIncidents);
            violation.setRootCauseAnalysis(rootCauseAnalysis);
            violation.setIncidentTimeline(incidentTimeline);
            violation.setResolutionPlan(resolutionPlan);
            violation.setRemediationActions(remediationActions);
            violation.setRemediationPlan(remediationPlan);
            violation.setRemediationStatus(remediationStatus);
            violation.setTargetResolutionDate(targetResolutionDate);
            violation.setActualResolutionDate(actualResolutionDate);
            violation.setResolutionSummary(resolutionSummary);
            violation.setResolved(isResolved);
            violation.setReportingRequirements(reportingRequirements);
            violation.setNotificationsSent(notificationsSent);
            violation.setComplianceReports(complianceReports);
            violation.setAuditTrail(auditTrail);
            violation.setFinancialAssessment(financialAssessment);
            violation.setLegalAssessment(legalAssessment);
            violation.setPotentialPenalties(potentialPenalties);
            violation.setInsuranceClaim(insuranceClaim);
            violation.setAssignedTeams(assignedTeams);
            violation.setPrimaryOwner(primaryOwner);
            violation.setComplianceOfficer(complianceOfficer);
            violation.setEscalationMatrix(escalationMatrix);
            violation.setStakeholderNotifications(stakeholderNotifications);
            violation.setFailedControls(failedControls);
            violation.setRecommendedControls(recommendedControls);
            violation.setPreventionMeasures(preventionMeasures);
            violation.setControlEffectiveness(controlEffectiveness);
            return violation;
        }
    }
    */
}

enum ComplianceViolationType {
    DATA_BREACH("Data Breach"),
    PRIVACY_VIOLATION("Privacy Violation"),
    SECURITY_CONTROL_FAILURE("Security Control Failure"),
    ACCESS_CONTROL_VIOLATION("Access Control Violation"),
    AUDIT_TRAIL_FAILURE("Audit Trail Failure"),
    RETENTION_POLICY_VIOLATION("Retention Policy Violation"),
    ENCRYPTION_FAILURE("Encryption Failure"),
    UNAUTHORIZED_ACCESS("Unauthorized Access"),
    DATA_MISCLASSIFICATION("Data Misclassification"),
    POLICY_VIOLATION("Policy Violation"),
    REGULATORY_REPORTING_FAILURE("Regulatory Reporting Failure"),
    THIRD_PARTY_COMPLIANCE_FAILURE("Third Party Compliance Failure");
    
    private final String displayName;
    
    ComplianceViolationType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}


enum ViolationStatus {
    ACTIVE, INVESTIGATING, REMEDIATING, RESOLVED, CLOSED, DEFERRED
}

enum ComplianceDomain {
    DATA_PROTECTION, PRIVACY, SECURITY, FINANCIAL, OPERATIONAL, REGULATORY
}


enum RemediationPriority {
    EMERGENCY, HIGH, MEDIUM, LOW, DEFERRED
}

// Supporting classes for comprehensive compliance violation management

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CustomerImpact {
    private int affectedCustomers;
    private String impactDescription;
    private boolean requiresCustomerNotification;
    private List<String> notificationChannels;
    private LocalDateTime notificationDeadline;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResolutionPlan {
    private String planDescription;
    private String estimatedEffort;
    private LocalDateTime startDate;
    private LocalDateTime expectedCompletionDate;
    private List<String> requiredResources;
    private List<String> dependencies;
    private List<String> milestones;
    
    public String getEstimatedEffort() {
        return estimatedEffort;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RemediationAction {
    private String actionId;
    private String description;
    private String assignee;
    private LocalDateTime dueDate;
    private String status;
    private String completionNotes;
    private LocalDateTime completedAt;
}


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ComplianceReport {
    private String reportId;
    private String reportType;
    private LocalDateTime submittedAt;
    private String submittedTo;
    private String reportStatus;
    private String reportSummary;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AuditTrail {
    private List<String> actions;
    private List<String> accessLogs;
    private List<String> modifications;
    private Map<String, LocalDateTime> timestamps;
    private boolean isComplete;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class FinancialAssessment {
    private double estimatedPenalty;
    private double remediationCost;
    private double businessLoss;
    private double legalCosts;
    private String currency;
    private boolean insuranceCoverage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class LegalAssessment {
    private String legalRisk;
    private List<String> potentialLiabilities;
    private boolean litigationRisk;
    private String counselRecommendation;
    private LocalDateTime assessmentDate;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PotentialPenalty {
    private String regulatoryBody;
    private String penaltyType;
    private double minAmount;
    private double maxAmount;
    private String calculationBasis;
    private double probability;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InsuranceClaim {
    private String claimId;
    private String insuranceProvider;
    private double claimAmount;
    private String claimStatus;
    private LocalDateTime filedDate;
    private String coverage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class EscalationMatrix {
    private Map<String, String> escalationLevels;
    private Map<String, LocalDateTime> escalationTimes;
    private List<String> notificationRecipients;
    private String currentEscalationLevel;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StakeholderNotification {
    private String stakeholder;
    private String notificationMethod;
    private LocalDateTime sentAt;
    private String status;
    private String response;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PreventionMeasures {
    private List<String> immediateControls;
    private List<String> longTermControls;
    private String controlFramework;
    private LocalDateTime implementationDeadline;
    private String controlOwner;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ControlEffectiveness {
    private Map<String, String> controlStatus;
    private double overallEffectiveness;
    private List<String> gaps;
    private List<String> improvements;
    private LocalDateTime lastAssessment;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class IncidentTimeline {
    private LocalDateTime incidentDetected;
    private LocalDateTime firstResponse;
    private LocalDateTime containmentStarted;
    private LocalDateTime containmentCompleted;
    private LocalDateTime investigationStarted;
    private LocalDateTime investigationCompleted;
    private LocalDateTime remediationStarted;
    private LocalDateTime remediationCompleted;
    private List<TimelineEvent> events;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private LocalDateTime timestamp;
        private String eventType;
        private String description;
        private String actor;
        private String impact;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RemediationPlan {
    private String planId;
    private String description;
    private String estimatedEffort;
    private LocalDateTime startDate;
    private LocalDateTime expectedCompletionDate;
    private List<String> requiredResources;
    private List<String> dependencies;
    private List<String> milestones;
    private List<RemediationStep> steps;
    private String owner;
    private String approver;
    private RemediationStatus status;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemediationStep {
        private String stepId;
        private String description;
        private String assignee;
        private LocalDateTime dueDate;
        private String status;
        private String completionNotes;
        private LocalDateTime completedAt;
    }
    
    public String getEstimatedEffort() {
        return estimatedEffort;
    }
}

enum RemediationStatus {
    PLANNED, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED, DEFERRED
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RootCauseAnalysis {
    private String analysisId;
    private String primaryRootCause;
    private List<String> contributingFactors;
    private String analysisMethod;
    private String analyst;
    private LocalDateTime analysisDate;
    private LocalDateTime completedDate;
    private String confidence;
    private List<Evidence> evidence;
    private List<String> recommendations;
    private String preventionStrategy;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String evidenceType;
        private String description;
        private String source;
        private LocalDateTime collectedAt;
        private String reliability;
    }
}