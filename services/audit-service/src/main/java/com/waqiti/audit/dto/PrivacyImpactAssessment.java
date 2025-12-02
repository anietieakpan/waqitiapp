package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Privacy Impact Assessment (PIA) for GDPR compliance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyImpactAssessment {
    private String assessmentId;
    private String projectName;
    private String projectDescription;
    private LocalDateTime assessmentDate;
    private String assessedBy;
    private String status;
    
    // Data processing details
    private List<String> dataCategories;
    private List<String> dataSubjects;
    private String processingPurpose;
    private String legalBasis;
    private List<String> dataRecipients;
    private String retentionPeriod;
    private List<String> internationalTransfers;
    
    // Risk assessment
    private String necessityAssessment;
    private String proportionalityAssessment;
    private List<PrivacyRisk> identifiedRisks;
    private String overallRiskLevel;
    
    // Security measures
    private List<String> technicalMeasures;
    private List<String> organizationalMeasures;
    private List<String> dataMinimizationMeasures;
    
    // Consultation
    private Boolean dpoConsulted;
    private LocalDateTime dpoConsultationDate;
    private String dpoRecommendations;
    private Boolean supervisoryAuthorityConsulted;
    private String supervisoryAuthorityFeedback;
    
    // Compliance
    private Map<String, Boolean> gdprPrinciples;
    private Map<String, String> dataSubjectRights;
    private String complianceStatus;
    private List<String> recommendations;
    private LocalDateTime nextReviewDate;
}

