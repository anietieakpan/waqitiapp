package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud rule violation with detailed tracking and analysis
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleViolation {
    
    private String violationId;
    private String ruleId;
    private String ruleName;
    private String evaluationId;
    
    // Transaction context
    private String transactionId;
    private String userId;
    private String accountId;
    private String sessionId;
    private String deviceId;
    private String ipAddress;
    
    // Violation details
    private ViolationType violationType;
    private String ruleType; // String version for compatibility
    private ViolationSeverity severity;
    private Double violationScore;
    private Double severityScore; // Numeric severity score for calculations
    private String violationDescription;
    private String specificCondition;
    
    // Threshold analysis
    private String violatedThreshold;
    private Object thresholdValue;
    private Object actualValue;
    private Double exceedancePercentage;
    private String comparisonType;
    
    // Detection details
    private LocalDateTime detectedAt;
    private String detectionMethod;
    private Long detectionLatencyMs;
    private Double confidence;
    private String detectionContext;
    
    // Impact assessment
    private BigDecimal potentialLossAmount;
    private String impactCategory;
    private ImpactLevel businessImpact;
    private ImpactLevel reputationalImpact;
    private ImpactLevel regulatoryImpact;
    
    // Violation classification
    private RiskClassification riskClassification;
    private UrgencyLevel urgencyLevel;
    private String primaryRiskFactor;
    private List<String> contributingFactors;
    
    // Historical context
    private Integer previousViolationCount;
    private LocalDateTime lastViolationTime;
    private Long daysSinceLastViolation;
    private String violationPattern;
    private Boolean isRepeatingViolation;
    
    // Response and actions
    private String recommendedAction; // PRODUCTION FIX: Added for ComprehensiveFraudBlacklistService
    private List<String> triggeredActions;
    private String primaryAction;
    private String actionStatus;
    private LocalDateTime actionTakenAt;
    private String actionResult;
    
    // Investigation details
    private String investigationStatus;
    private String assignedInvestigator;
    private LocalDateTime investigationStarted;
    private String investigationNotes;
    private String investigationOutcome;
    
    // Resolution tracking
    private ResolutionStatus resolutionStatus;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String resolutionMethod;
    private String resolutionNotes;
    private Boolean isFalsePositive;
    
    // Compliance and reporting
    private Boolean requiresRegulatoryReporting;
    private String regulatoryCategory;
    private List<String> applicableRegulations;
    private LocalDateTime reportingDeadline;
    private String reportingStatus;
    
    // Additional metadata
    private Map<String, Object> violationData;
    private Map<String, String> customAttributes;
    private List<String> tags;
    private String notes;
    
    /**
     * Types of rule violations
     */
    public enum ViolationType {
        THRESHOLD_EXCEEDED,     // Numeric threshold violation
        PATTERN_DETECTED,       // Suspicious pattern identified
        BEHAVIOR_ANOMALY,       // Behavioral anomaly detected
        VELOCITY_VIOLATION,     // Rate/velocity limit exceeded
        BLACKLIST_MATCH,       // Blacklist entity matched
        GEOGRAPHIC_VIOLATION,   // Geographic restriction violated
        TIME_RESTRICTION,       // Time-based rule violated
        NETWORK_VIOLATION,      // Network analysis violation
        COMPLIANCE_VIOLATION,   // Regulatory compliance violated
        POLICY_VIOLATION,       // Internal policy violated
        STATISTICAL_ANOMALY,    // Statistical outlier detected
        ML_PREDICTION          // Machine learning prediction
    }
    
    /**
     * Violation severity levels
     */
    public enum ViolationSeverity {
        INFORMATIONAL(0.1),
        LOW(0.3),
        MEDIUM(0.5),
        HIGH(0.7),
        CRITICAL(0.9),
        EMERGENCY(1.0);
        
        private final double scoreWeight;
        
        ViolationSeverity(double scoreWeight) {
            this.scoreWeight = scoreWeight;
        }
        
        public double getScoreWeight() {
            return scoreWeight;
        }

        public double getWeight() {
            return scoreWeight;
        }
    }
    
    /**
     * Impact levels for different aspects
     */
    public enum ImpactLevel {
        NONE,
        MINIMAL,
        LOW,
        MODERATE,
        HIGH,
        SEVERE,
        CATASTROPHIC
    }
    
    /**
     * Risk classification levels
     */
    public enum RiskClassification {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        EXTREME
    }
    
    /**
     * Urgency levels for response
     */
    public enum UrgencyLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        IMMEDIATE,
        EMERGENCY
    }
    
    /**
     * Resolution status tracking
     */
    public enum ResolutionStatus {
        OPEN,
        UNDER_INVESTIGATION,
        PENDING_REVIEW,
        AWAITING_ACTION,
        RESOLVED,
        CLOSED,
        FALSE_POSITIVE,
        ESCALATED
    }
    
    /**
     * Get severity score (returns severityScore or calculates from severity)
     */
    public Double getSeverityScore() {
        if (severityScore != null) {
            return severityScore;
        }
        if (severity != null) {
            return severity.getScoreWeight();
        }
        return 0.5; // Default medium severity
    }

    /**
     * Calculate comprehensive violation score
     */
    public double calculateViolationScore() {
        double score = severity.getScoreWeight();
        
        // Apply confidence multiplier
        if (confidence != null) {
            score *= confidence;
        }
        
        // Apply exceedance multiplier
        if (exceedancePercentage != null) {
            double exceedanceMultiplier = 1.0 + (exceedancePercentage / 100.0);
            score *= Math.min(2.0, exceedanceMultiplier); // Cap at 2x
        }
        
        // Historical pattern multiplier
        if (isRepeatingViolation != null && isRepeatingViolation) {
            score *= 1.3;
        }
        
        if (previousViolationCount != null && previousViolationCount > 0) {
            double historyMultiplier = 1.0 + (previousViolationCount * 0.1);
            score *= Math.min(1.5, historyMultiplier); // Cap at 1.5x
        }
        
        // Impact multipliers
        if (businessImpact != null) {
            switch (businessImpact) {
                case HIGH:
                case SEVERE:
                case CATASTROPHIC:
                    score *= 1.2;
                    break;
                case MODERATE:
                    score *= 1.1;
                    break;
            }
        }
        
        // Regulatory multiplier
        if (requiresRegulatoryReporting != null && requiresRegulatoryReporting) {
            score *= 1.4;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Check if violation requires immediate action
     */
    public boolean requiresImmediateAction() {
        return urgencyLevel == UrgencyLevel.IMMEDIATE ||
               urgencyLevel == UrgencyLevel.EMERGENCY ||
               severity == ViolationSeverity.EMERGENCY ||
               severity == ViolationSeverity.CRITICAL ||
               (businessImpact == ImpactLevel.SEVERE || businessImpact == ImpactLevel.CATASTROPHIC);
    }
    
    /**
     * Generate violation risk assessment
     */
    public String generateRiskAssessment() {
        StringBuilder assessment = new StringBuilder();
        
        assessment.append("=== VIOLATION RISK ASSESSMENT ===\n");
        assessment.append("Violation ID: ").append(violationId).append("\n");
        assessment.append("Rule: ").append(ruleName).append("\n");
        assessment.append("Type: ").append(violationType).append("\n");
        assessment.append("Severity: ").append(severity).append("\n");
        assessment.append("Score: ").append(String.format("%.3f", calculateViolationScore())).append("\n\n");
        
        assessment.append("=== THRESHOLD ANALYSIS ===\n");
        if (violatedThreshold != null) {
            assessment.append("Violated Threshold: ").append(violatedThreshold).append("\n");
            assessment.append("Limit: ").append(thresholdValue).append("\n");
            assessment.append("Actual: ").append(actualValue).append("\n");
            if (exceedancePercentage != null) {
                assessment.append("Exceedance: ").append(String.format("%.1f%%", exceedancePercentage)).append("\n");
            }
        }
        
        assessment.append("\n=== IMPACT ASSESSMENT ===\n");
        if (potentialLossAmount != null) {
            assessment.append("Potential Loss: $").append(potentialLossAmount).append("\n");
        }
        if (businessImpact != null) {
            assessment.append("Business Impact: ").append(businessImpact).append("\n");
        }
        if (reputationalImpact != null) {
            assessment.append("Reputational Impact: ").append(reputationalImpact).append("\n");
        }
        if (regulatoryImpact != null) {
            assessment.append("Regulatory Impact: ").append(regulatoryImpact).append("\n");
        }
        
        if (previousViolationCount != null && previousViolationCount > 0) {
            assessment.append("\n=== HISTORICAL CONTEXT ===\n");
            assessment.append("Previous Violations: ").append(previousViolationCount).append("\n");
            if (daysSinceLastViolation != null) {
                assessment.append("Days Since Last: ").append(daysSinceLastViolation).append("\n");
            }
            if (violationPattern != null) {
                assessment.append("Pattern: ").append(violationPattern).append("\n");
            }
        }
        
        if (requiresRegulatoryReporting != null && requiresRegulatoryReporting) {
            assessment.append("\n=== REGULATORY REQUIREMENTS ===\n");
            assessment.append("Requires Reporting: YES\n");
            if (regulatoryCategory != null) {
                assessment.append("Category: ").append(regulatoryCategory).append("\n");
            }
            if (reportingDeadline != null) {
                assessment.append("Deadline: ").append(reportingDeadline).append("\n");
            }
        }
        
        return assessment.toString();
    }
    
    /**
     * Get recommended escalation level
     */
    public EscalationLevel getRecommendedEscalation() {
        double violationScore = calculateViolationScore();
        
        if (requiresImmediateAction() || violationScore > 0.9) {
            return EscalationLevel.EXECUTIVE;
        }
        
        if (severity == ViolationSeverity.HIGH || violationScore > 0.7) {
            return EscalationLevel.SENIOR_MANAGEMENT;
        }
        
        if (requiresRegulatoryReporting != null && requiresRegulatoryReporting) {
            return EscalationLevel.COMPLIANCE_OFFICER;
        }
        
        if (violationScore > 0.5) {
            return EscalationLevel.DEPARTMENT_MANAGER;
        }
        
        return EscalationLevel.TEAM_LEAD;
    }
    
    /**
     * Escalation levels
     */
    public enum EscalationLevel {
        NONE,
        TEAM_LEAD,
        DEPARTMENT_MANAGER,
        COMPLIANCE_OFFICER,
        SENIOR_MANAGEMENT,
        EXECUTIVE
    }
    
    /**
     * Calculate time to resolution based on severity and impact
     */
    public long calculateTargetResolutionTimeMinutes() {
        if (urgencyLevel == UrgencyLevel.EMERGENCY) return 30;
        if (urgencyLevel == UrgencyLevel.IMMEDIATE) return 60;
        if (severity == ViolationSeverity.CRITICAL) return 120;
        if (severity == ViolationSeverity.HIGH) return 480; // 8 hours
        if (requiresRegulatoryReporting != null && requiresRegulatoryReporting) return 1440; // 24 hours
        if (severity == ViolationSeverity.MEDIUM) return 2880; // 48 hours
        return 10080; // 7 days
    }
    
    /**
     * Check if violation is overdue for resolution
     */
    public boolean isOverdueForResolution() {
        if (resolutionStatus == ResolutionStatus.RESOLVED || 
            resolutionStatus == ResolutionStatus.CLOSED) {
            return false;
        }
        
        long targetMinutes = calculateTargetResolutionTimeMinutes();
        LocalDateTime targetTime = detectedAt.plusMinutes(targetMinutes);
        
        return LocalDateTime.now().isAfter(targetTime);
    }
    
    /**
     * Update violation resolution
     */
    public void resolveViolation(String resolvedBy, String method, String notes, boolean isFalsePositive) {
        this.resolutionStatus = isFalsePositive ? ResolutionStatus.FALSE_POSITIVE : ResolutionStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
        this.resolutionMethod = method;
        this.resolutionNotes = notes;
        this.isFalsePositive = isFalsePositive;
    }

    /**
     * Get rule violation type
     */
    public ViolationType ruleType() {
        return violationType;
    }

    /**
     * Get risk score (backward compatibility)
     */
    public Double getRiskScore() {
        return violationScore != null ? violationScore : getSeverityScore();
    }
}