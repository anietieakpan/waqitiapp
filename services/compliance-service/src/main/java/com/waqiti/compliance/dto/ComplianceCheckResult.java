package com.waqiti.compliance.dto;

import com.waqiti.compliance.domain.ComplianceAlert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResult {

    private String transactionId;

    private String customerId;

    private ComplianceStatus status;

    private String decision;

    private String riskLevel;

    private Double riskScore;

    private Boolean approved;

    private Boolean requiresManualReview;

    private Boolean requiresEnhancedDueDiligence;

    private Boolean requiresDocumentation;

    private Boolean blocked;

    private Boolean flaggedForReview;

    private List<ComplianceViolation> violations;

    private List<ComplianceAlert> alerts;

    private List<ComplianceRecommendation> recommendations;

    private List<ComplianceAction> requiredActions;

    private OFACScreeningResult ofacResult;

    private PEPScreeningResult pepResult;

    private SanctionsScreeningResult sanctionsResult;

    private AmountAnalysisResult amountAnalysis;

    private VelocityCheckResult velocityCheck;

    private PatternAnalysisResult patternAnalysis;

    private CustomerRiskAssessment customerRiskAssessment;

    private String complianceOfficer;

    private String reviewNotes;

    private String decisionReason;

    private LocalDateTime processedAt;

    private LocalDateTime expiresAt;

    private Integer processingTimeMs;

    private String checksum;

    private String metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationType;
        private String violationCode;
        private String description;
        private String severity;
        private String regulatoryReference;
        private String ruleId;
        private String ruleName;
        private String details;
        private String remedialAction;
        private Boolean requiresReporting;
        private Boolean requiresEscalation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceRecommendation {
        private String recommendationType;
        private String description;
        private String priority;
        private String timeframe;
        private String assignedTo;
        private String businessJustification;
        private List<String> actionItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceAction {
        private String actionType;
        private String description;
        private String priority;
        private String assignedTo;
        private String deadline;
        private String status;
        private String actionCode;
        private String parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OFACScreeningResult {
        private Boolean clean;
        private Boolean matched;
        private List<OFACMatch> matches;
        private String screeningId;
        private LocalDateTime screeningDate;
        private String screeningProvider;
        private String confidence;
        private String matchDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OFACMatch {
        private String matchType;
        private String listName;
        private String entityName;
        private String entityType;
        private String country;
        private String program;
        private String matchScore;
        private String matchReason;
        private String sanctions;
        private String effectiveDate;
        private String lastUpdateDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PEPScreeningResult {
        private Boolean pepMatch;
        private Boolean isCurrentPEP;
        private Boolean isFormerPEP;
        private Boolean isAssociatePEP;
        private String pepType;
        private String pepDetails;
        private String jurisdiction;
        private String position;
        private String riskLevel;
        private String screeningProvider;
        private LocalDateTime screeningDate;
        private List<PEPMatch> matches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PEPMatch {
        private String personName;
        private String position;
        private String jurisdiction;
        private String pepType;
        private String riskLevel;
        private String lastKnownRole;
        private String matchScore;
        private String sources;
        private String lastUpdateDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsScreeningResult {
        private Boolean sanctioned;
        private Boolean watchlisted;
        private List<SanctionsMatch> matches;
        private String screeningProvider;
        private LocalDateTime screeningDate;
        private String confidence;
        private String details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsMatch {
        private String listName;
        private String entityName;
        private String entityType;
        private String country;
        private String program;
        private String sanctionType;
        private String matchScore;
        private String effectiveDate;
        private String expiryDate;
        private String authority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountAnalysisResult {
        private Boolean requiresCTR;
        private Boolean suspicious;
        private Boolean unusualAmount;
        private Boolean roundAmount;
        private Boolean structuringDetected;
        private String suspiciousReason;
        private String amountPattern;
        private String riskLevel;
        private Double riskScore;
        private String analysis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityCheckResult {
        private Boolean velocityExceeded;
        private Boolean frequencyExceeded;
        private Boolean volumeExceeded;
        private String velocityType;
        private String timeWindow;
        private String threshold;
        private String actualValue;
        private String riskLevel;
        private String details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternAnalysisResult {
        private Boolean hasSuspiciousPatterns;
        private List<SuspiciousPattern> suspiciousPatterns;
        private String overallRiskLevel;
        private Double patternScore;
        private String analysis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousPattern {
        private String patternType;
        private String description;
        private String severity;
        private String confidence;
        private String timeframe;
        private String details;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRiskAssessment {
        private Boolean highRisk;
        private String riskLevel;
        private Double riskScore;
        private String riskCategory;
        private String riskFactors;
        private String assessment;
        private String recommendations;
        private LocalDateTime assessmentDate;
        private String assessedBy;
    }

    public enum ComplianceStatus {
        COMPLIANT,
        NON_COMPLIANT,
        REQUIRES_REVIEW,
        PENDING_DOCUMENTATION,
        ESCALATED,
        BLOCKED
    }

    public enum DecisionType {
        APPROVED,
        REJECTED,
        PENDING,
        ESCALATED,
        BLOCKED,
        REQUIRES_REVIEW
    }

    public boolean isCompliant() {
        return status == ComplianceStatus.COMPLIANT;
    }

    public boolean requiresAction() {
        return requiresManualReview || requiresEnhancedDueDiligence || requiresDocumentation;
    }

    public boolean isBlocked() {
        return blocked != null && blocked;
    }

    public boolean hasViolations() {
        return violations != null && !violations.isEmpty();
    }

    public boolean hasAlerts() {
        return alerts != null && !alerts.isEmpty();
    }

    public boolean hasHighRiskIndicators() {
        return isHighRisk() || hasViolations() || hasAlerts();
    }

    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "VERY_HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }
}