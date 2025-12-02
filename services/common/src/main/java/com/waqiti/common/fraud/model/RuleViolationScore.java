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
 * Comprehensive scoring for rule violations with risk assessment
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class RuleViolationScore {
    
    private String scoreId;
    private String transactionId;
    private String userId;
    private String sessionId;
    
    // Overall scoring
    private Double overallViolationScore;
    private Double overallScore; // PRODUCTION FIX: Alias for builder compatibility
    private Double score; // Alias for overallViolationScore for compatibility
    private FraudRiskLevel riskLevel;
    private String scoreCategory;
    private String dominantViolationType;
    
    // Component scores
    private Double thresholdViolationScore;
    private Double patternViolationScore;
    private Double behaviorViolationScore;
    private Double velocityViolationScore;
    private Double complianceViolationScore;
    private Double networkViolationScore;
    
    // Violation details
    private List<FraudRuleViolation> violations;
    private Integer totalViolationCount;
    private Integer violationCount; // Alias for totalViolationCount
    private Integer criticalViolations;
    private Integer criticalCount; // Alias for criticalViolations for builder compatibility
    private Integer highSeverityCount; // PRODUCTION FIX: High severity violation count
    private Integer highPriorityViolations;
    private Integer mediumPriorityViolations;
    private Integer lowPriorityViolations;
    
    // Scoring metadata
    private LocalDateTime calculatedAt;
    private String scoringMethod;
    private String scoringModelVersion;
    private Long calculationDurationMs;
    private Double confidence;
    
    // Historical violation analysis
    private Integer historicalViolationCount;
    private LocalDateTime earliestViolation;
    private LocalDateTime latestViolation;
    private String violationTrend;
    private Boolean hasEscalatingPattern;
    
    // Severity distribution
    private Map<String, Integer> violationsBySeverity;
    private Map<String, Integer> violationsByType;
    private Map<String, Double> severityWeightedScores;
    private String mostFrequentViolationType;
    
    // Impact assessment
    private BigDecimal estimatedFinancialImpact;
    private String businessRiskLevel;
    private String reputationalRiskLevel;
    private String regulatoryRiskLevel;
    private String operationalRiskLevel;
    
    // Temporal analysis
    private String timePattern;
    private Integer violationsLast24Hours;
    private Integer violationsLastWeek;
    private Integer violationsLastMonth;
    private Double violationFrequencyScore;
    
    // Correlation analysis
    private List<String> correlatedEntities;
    private Map<String, Double> entityCorrelationScores;
    private Boolean partOfLargerPattern;
    private String correlationInsights;
    
    // Risk factors and indicators
    private List<String> primaryRiskFactors;
    private List<String> secondaryRiskFactors;
    private List<String> mitigatingFactors;
    private Map<String, Double> riskFactorWeights;
    
    // Action and response tracking
    private List<String> triggeredActions;
    private String recommendedPrimaryAction;
    private FraudRuleViolation.UrgencyLevel actionUrgency;
    private Boolean requiresImmediateEscalation;
    private String escalationReason;
    
    // Quality and validation
    private Double scoringQuality;
    private List<String> scoringLimitations;
    private String validationStatus;
    private LocalDateTime lastValidated;
    
    // Additional analysis
    private Map<String, Object> customMetrics;
    private List<String> anomalies;
    private String overallAssessment;
    
    /**
     * Get score value (returns score or overallViolationScore)
     */
    public Double getScore() {
        return score != null ? score : overallViolationScore;
    }

    /**
     * PRODUCTION FIX: Get critical violation count
     */
    public Integer getCriticalViolationCount() {
        return criticalViolations != null ? criticalViolations : 0;
    }

    /**
     * Calculate comprehensive weighted violation score
     */
    public double calculateWeightedViolationScore() {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // Weight different violation types based on severity
        if (complianceViolationScore != null) {
            score += complianceViolationScore * 0.3; // Highest weight
            totalWeight += 0.3;
        }
        
        if (thresholdViolationScore != null) {
            score += thresholdViolationScore * 0.25;
            totalWeight += 0.25;
        }
        
        if (patternViolationScore != null) {
            score += patternViolationScore * 0.2;
            totalWeight += 0.2;
        }
        
        if (velocityViolationScore != null) {
            score += velocityViolationScore * 0.15;
            totalWeight += 0.15;
        }
        
        if (behaviorViolationScore != null) {
            score += behaviorViolationScore * 0.07;
            totalWeight += 0.07;
        }
        
        if (networkViolationScore != null) {
            score += networkViolationScore * 0.03;
            totalWeight += 0.03;
        }
        
        // Normalize by actual weights
        if (totalWeight > 0) {
            score = score / totalWeight;
        }
        
        // Apply historical pattern multipliers
        if (hasEscalatingPattern != null && hasEscalatingPattern) {
            score *= 1.3;
        }
        
        if (historicalViolationCount != null && historicalViolationCount > 5) {
            double historyMultiplier = 1.0 + Math.min(0.5, historicalViolationCount * 0.02);
            score *= historyMultiplier;
        }
        
        // Apply frequency multiplier
        if (violationFrequencyScore != null) {
            score *= (1.0 + violationFrequencyScore * 0.2);
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get the most severe violation from the list
     */
    public FraudRuleViolation getMostSevereViolation() {
        if (violations == null || violations.isEmpty()) {
            return null;
        }
        
        return violations.stream()
            .max((v1, v2) -> {
                double score1 = v1.calculateViolationScore();
                double score2 = v2.calculateViolationScore();
                return Double.compare(score1, score2);
            })
            .orElse(null);
    }
    
    /**
     * Check if score indicates systematic fraud operation
     */
    public boolean indicatesSystematicFraud() {
        return (partOfLargerPattern != null && partOfLargerPattern) ||
               (hasEscalatingPattern != null && hasEscalatingPattern) ||
               (criticalViolations != null && criticalViolations > 2) ||
               (totalViolationCount != null && totalViolationCount > 10) ||
               (violationFrequencyScore != null && violationFrequencyScore > 0.8);
    }
    
    /**
     * Calculate violation diversity score
     */
    public double calculateViolationDiversity() {
        if (violationsByType == null || violationsByType.isEmpty()) {
            return 0.0;
        }
        
        int totalViolations = violationsByType.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
        
        if (totalViolations == 0) return 0.0;
        
        // Calculate Shannon entropy for diversity
        double diversity = 0.0;
        for (int count : violationsByType.values()) {
            if (count > 0) {
                double probability = (double) count / totalViolations;
                diversity -= probability * Math.log(probability) / Math.log(2);
            }
        }
        
        // Normalize to 0-1 scale
        double maxEntropy = Math.log(violationsByType.size()) / Math.log(2);
        return maxEntropy > 0 ? diversity / maxEntropy : 0.0;
    }
    
    /**
     * Generate comprehensive violation analysis report
     */
    public String generateViolationAnalysisReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== RULE VIOLATION SCORE ANALYSIS ===\n");
        report.append("Score ID: ").append(scoreId).append("\n");
        report.append("Transaction: ").append(transactionId).append("\n");
        report.append("Overall Score: ").append(String.format("%.3f", calculateWeightedViolationScore())).append("\n");
        report.append("Risk Level: ").append(riskLevel).append("\n\n");
        
        report.append("=== VIOLATION BREAKDOWN ===\n");
        if (totalViolationCount != null && totalViolationCount > 0) {
            report.append("Total Violations: ").append(totalViolationCount).append("\n");
            if (criticalViolations != null && criticalViolations > 0) {
                report.append("Critical: ").append(criticalViolations).append("\n");
            }
            if (highPriorityViolations != null && highPriorityViolations > 0) {
                report.append("High Priority: ").append(highPriorityViolations).append("\n");
            }
            if (dominantViolationType != null) {
                report.append("Dominant Type: ").append(dominantViolationType).append("\n");
            }
        }
        
        report.append("\n=== COMPONENT SCORES ===\n");
        if (complianceViolationScore != null && complianceViolationScore > 0.1) {
            report.append("Compliance: ").append(String.format("%.3f", complianceViolationScore)).append("\n");
        }
        if (thresholdViolationScore != null && thresholdViolationScore > 0.1) {
            report.append("Threshold: ").append(String.format("%.3f", thresholdViolationScore)).append("\n");
        }
        if (patternViolationScore != null && patternViolationScore > 0.1) {
            report.append("Pattern: ").append(String.format("%.3f", patternViolationScore)).append("\n");
        }
        if (velocityViolationScore != null && velocityViolationScore > 0.1) {
            report.append("Velocity: ").append(String.format("%.3f", velocityViolationScore)).append("\n");
        }
        
        if (indicatesSystematicFraud()) {
            report.append("\n=== SYSTEMATIC FRAUD INDICATORS ===\n");
            if (partOfLargerPattern != null && partOfLargerPattern) {
                report.append("• Part of larger fraud pattern\n");
            }
            if (hasEscalatingPattern != null && hasEscalatingPattern) {
                report.append("• Shows escalating violation pattern\n");
            }
            if (violationFrequencyScore != null && violationFrequencyScore > 0.8) {
                report.append("• High violation frequency detected\n");
            }
        }
        
        if (estimatedFinancialImpact != null && estimatedFinancialImpact.compareTo(BigDecimal.ZERO) > 0) {
            report.append("\n=== FINANCIAL IMPACT ===\n");
            report.append("Estimated Loss: $").append(estimatedFinancialImpact).append("\n");
        }
        
        if (primaryRiskFactors != null && !primaryRiskFactors.isEmpty()) {
            report.append("\n=== PRIMARY RISK FACTORS ===\n");
            primaryRiskFactors.forEach(factor -> 
                report.append("• ").append(factor).append("\n"));
        }
        
        if (recommendedPrimaryAction != null) {
            report.append("\n=== RECOMMENDED ACTIONS ===\n");
            report.append("Primary Action: ").append(recommendedPrimaryAction).append("\n");
            report.append("Urgency: ").append(actionUrgency).append("\n");
            if (requiresImmediateEscalation != null && requiresImmediateEscalation) {
                report.append("Requires Immediate Escalation: ").append(escalationReason).append("\n");
            }
        }
        
        return report.toString();
    }
    
    /**
     * Get recommended response actions based on violation analysis
     */
    public List<String> getRecommendedResponseActions() {
        double score = calculateWeightedViolationScore();
        
        if (indicatesSystematicFraud() || score > 0.9) {
            return List.of(
                "ESCALATE_TO_SPECIALIST",
                "FREEZE_ALL_ACCOUNTS", 
                "NOTIFY_LAW_ENFORCEMENT",
                "PRESERVE_EVIDENCE",
                "GENERATE_SAR",
                "COORDINATE_INVESTIGATION"
            );
        }
        
        if (requiresImmediateEscalation != null && requiresImmediateEscalation) {
            return List.of(
                "ESCALATE_TO_SENIOR_ANALYST",
                "FREEZE_ACCOUNT",
                "BLOCK_TRANSACTION",
                "NOTIFY_COMPLIANCE",
                "ENABLE_ENHANCED_MONITORING"
            );
        }
        
        if (score > 0.7 || (criticalViolations != null && criticalViolations > 0)) {
            return List.of(
                "ESCALATE_TO_ANALYST",
                "REQUIRE_ADDITIONAL_AUTH",
                "LIMIT_TRANSACTION_AMOUNT",
                "ENABLE_ENHANCED_MONITORING",
                "LOG_SECURITY_EVENT"
            );
        }
        
        if (score > 0.5) {
            return List.of(
                "ENABLE_ENHANCED_MONITORING",
                "SEND_SECURITY_ALERT",
                "LOG_SECURITY_EVENT",
                "REVIEW_WITHIN_24H"
            );
        }
        
        return List.of("LOG_SECURITY_EVENT", "ROUTINE_REVIEW");
    }
    
    /**
     * Calculate time criticality score
     */
    public double calculateTimeCriticality() {
        double criticality = 0.0;
        
        // Frequency-based criticality
        if (violationsLast24Hours != null && violationsLast24Hours > 5) {
            criticality += 0.4;
        } else if (violationsLast24Hours != null && violationsLast24Hours > 2) {
            criticality += 0.2;
        }
        
        // Pattern-based criticality
        if (hasEscalatingPattern != null && hasEscalatingPattern) {
            criticality += 0.3;
        }
        
        // Severity-based criticality
        if (criticalViolations != null && criticalViolations > 0) {
            criticality += 0.3;
        }
        
        return Math.min(1.0, criticality);
    }
    
    /**
     * Check if violations require regulatory reporting
     */
    public boolean requiresRegulatoryReporting() {
        if (violations == null) return false;
        
        return violations.stream()
            .anyMatch(violation -> 
                violation.getRequiresRegulatoryReporting() != null && 
                violation.getRequiresRegulatoryReporting());
    }
}