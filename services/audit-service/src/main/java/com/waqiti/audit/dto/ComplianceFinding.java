package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/**
 * DTO for compliance audit findings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceFinding {
    private String findingId;
    private String findingType;
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    private String status; // OPEN, IN_PROGRESS, RESOLVED, ACCEPTED
    
    // Finding details
    private String title;
    private String description;
    private String category;
    private List<String> affectedSystems;
    private List<String> affectedProcesses;
    private LocalDateTime identifiedDate;
    private String identifiedBy;
    
    // Compliance context
    private String regulatoryFramework; // SOX, PCI_DSS, GDPR, BASEL_III
    private String controlReference;
    private String requirementReference;
    private String complianceStandard;
    private Boolean isRegulatory;
    
    // Risk assessment
    private String riskRating;
    private String likelihood;
    private String businessImpact;
    private String financialImpact;
    private String reputationalImpact;
    private String regulatoryImpact;
    
    // Root cause analysis
    private String rootCause;
    private List<String> contributingFactors;
    private String processGap;
    private String controlWeakness;
    
    // Remediation
    private String remediationPlan;
    private String responsibleParty;
    private LocalDateTime targetResolutionDate;
    private LocalDateTime actualResolutionDate;
    private List<RemediationStep> remediationSteps;
    private String remediationStatus;
    private BigDecimal remediationCost;
    
    // Evidence and documentation
    private List<String> evidenceLinks;
    private List<String> supportingDocuments;
    private Map<String, String> auditTrail;
    
    // Management response
    private String managementResponse;
    private String managementAction;
    private LocalDateTime managementResponseDate;
    private Boolean managementAcceptance;
    
    // Follow-up
    private List<FollowUpAction> followUpActions;
    private LocalDateTime nextReviewDate;
    private String recurringPattern;
    private Integer occurrenceCount;
}

