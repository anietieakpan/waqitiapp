package com.waqiti.common.fraud;

import com.waqiti.common.fraud.ml.MLPredictionResult;
import com.waqiti.common.fraud.model.BehavioralAnomaly;
import com.waqiti.common.fraud.model.PatternMatch;
import com.waqiti.common.fraud.rules.RuleEvaluationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.waqiti.common.fraud.model.FraudScore;

import com.waqiti.common.fraud.model.FraudScore;

/**
 * Comprehensive result of fraud analysis containing all detection results,
 * recommendations, and processing metadata. This is the primary output
 * of the fraud detection system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResult {
    
    /**
     * Unique analysis result identifier
     */
    private String analysisId;
    
    /**
     * Transaction that was analyzed
     */
    private String transactionId;
    
    /**
     * User associated with the transaction
     */
    private String userId;
    
    /**
     * Overall fraud decision
     */
    private FraudDecision decision;
    
    /**
     * Comprehensive fraud score
     */
    private FraudScore fraudScore;
    
    /**
     * Machine learning prediction results
     */
    private MLPredictionResult mlPrediction;
    
    /**
     * Fraud rule evaluation results
     */
    private List<RuleEvaluationResult> ruleResults;
    
    /**
     * Risk factor analysis results
     */
    private Map<String, Double> riskFactors;
    
    /**
     * Generated fraud alerts
     */
    private List<FraudAlert> alerts;

    /**
     * Overall risk level
     */
    private com.waqiti.common.fraud.model.FraudRiskLevel riskLevel;

    /**
     * Rule violations detected
     */
    private List<com.waqiti.common.fraud.rules.FraudRuleViolation> ruleViolations;

    /**
     * Behavioral anomalies detected
     */
    private List<BehavioralAnomaly> behavioralAnomalies;

    /**
     * Pattern matches found
     */
    private List<PatternMatch> patternMatches;

    /**
     * Recommended actions to take
     */
    private List<RecommendedAction> recommendedActions;

    /**
     * Reasons for the fraud decision
     */
    private List<String> decisionReasons;
    
    /**
     * Processing performance metrics
     */
    private ProcessingMetrics processingMetrics;
    
    /**
     * Analysis confidence level (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * Analysis quality score (0.0 to 1.0)
     */
    private double qualityScore;
    
    /**
     * Analysis timestamp
     */
    @Builder.Default
    private LocalDateTime analysisTimestamp = LocalDateTime.now();
    
    /**
     * Fraud detection system version
     */
    private String systemVersion;
    
    /**
     * Additional context and metadata
     */
    private Map<String, Object> metadata;

    /**
     * Error message if analysis failed
     */
    private String error;
    
    /**
     * Business impact assessment
     */
    private BusinessImpact businessImpact;
    
    /**
     * Compliance and regulatory information
     */
    private ComplianceInfo complianceInfo;
    
    /**
     * Historical comparison results
     */
    private HistoricalComparison historicalComparison;
    
    /**
     * External validation results
     */
    private ExternalValidation externalValidation;
    
    /**
     * Check if transaction should be approved
     */
    public boolean shouldApprove() {
        return decision == FraudDecision.APPROVE;
    }
    
    /**
     * Check if transaction should be declined
     */
    public boolean shouldDecline() {
        return decision == FraudDecision.DECLINE;
    }
    
    /**
     * Check if transaction requires review
     */
    public boolean requiresReview() {
        return decision == FraudDecision.REVIEW || decision == FraudDecision.MANUAL_REVIEW;
    }
    
    /**
     * Check if additional authentication is required
     */
    public boolean requiresAuthentication() {
        return decision == FraudDecision.REQUIRE_AUTH;
    }
    
    /**
     * Get highest severity alert
     */
    public FraudAlert.AlertPriority getHighestAlertPriority() {
        if (alerts == null || alerts.isEmpty()) {
            return null;
        }
        
        return alerts.stream()
                .map(FraudAlert::getPriority)
                .max(Enum::compareTo)
                .orElse(null);
    }
    
    /**
     * Get count of triggered rules by severity
     */
    public Map<com.waqiti.common.fraud.rules.FraudRule.RuleSeverity, Long> getTriggeredRulesBySeverity() {
        if (ruleResults == null) {
            return Map.of();
        }
        
        return ruleResults.stream()
                .filter(RuleEvaluationResult::isTriggered)
                .collect(Collectors.groupingBy(
                    RuleEvaluationResult::getSeverity,
                    Collectors.counting()
                ));
    }
    
    /**
     * Get total weighted risk score from all sources
     */
    public double getTotalRiskScore() {
        double total = 0.0;
        
        if (fraudScore != null) {
            total += fraudScore.getOverallScore() * 0.5; // 50% weight
        }
        
        if (mlPrediction != null) {
            total += mlPrediction.getFraudProbability() * 0.3; // 30% weight
        }
        
        if (ruleResults != null) {
            double rulesScore = ruleResults.stream()
                    .filter(RuleEvaluationResult::isTriggered)
                    .mapToDouble(RuleEvaluationResult::getWeightedRiskScore)
                    .average()
                    .orElse(0.0);
            total += rulesScore * 0.2; // 20% weight
        }
        
        return Math.min(1.0, total);
    }
    
    /**
     * Check if analysis result is high confidence
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8 && qualityScore >= 0.7;
    }
    
    /**
     * Get primary decision reason
     */
    public String getPrimaryDecisionReason() {
        if (decisionReasons == null || decisionReasons.isEmpty()) {
            return "No specific reason provided";
        }
        return decisionReasons.get(0);
    }
    
    /**
     * Get processing efficiency score
     */
    public double getProcessingEfficiency() {
        if (processingMetrics == null) {
            return 0.0;
        }
        
        // Score based on processing time (lower is better)
        long totalTime = processingMetrics.getTotalProcessingTimeMs();
        if (totalTime <= 100) return 1.0;      // Excellent
        if (totalTime <= 500) return 0.8;      // Good
        if (totalTime <= 1000) return 0.6;     // Fair
        if (totalTime <= 2000) return 0.4;     // Poor
        return 0.2;                             // Very poor
    }
    
    /**
     * Create comprehensive analysis summary
     */
    public String createAnalysisSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("=== FRAUD ANALYSIS RESULT ===\n");
        summary.append(String.format("Analysis ID: %s\n", analysisId));
        summary.append(String.format("Transaction: %s\n", transactionId));
        summary.append(String.format("User: %s\n", userId));
        summary.append(String.format("Decision: %s\n", decision));
        summary.append(String.format("Total Risk Score: %.3f (%.1f%%)\n", getTotalRiskScore(), getTotalRiskScore() * 100));
        summary.append(String.format("Confidence: %.3f (%.1f%%)\n", confidence, confidence * 100));
        summary.append(String.format("Quality: %.3f (%.1f%%)\n", qualityScore, qualityScore * 100));
        summary.append(String.format("High Confidence: %s\n", isHighConfidence()));
        
        if (fraudScore != null) {
            summary.append(String.format("Fraud Score: %.3f (%s)\n", 
                fraudScore.getOverallScore(), fraudScore.getRiskLevel()));
        }
        
        if (mlPrediction != null) {
            summary.append(String.format("ML Prediction: %.3f (Fraud: %s)\n", 
                mlPrediction.getFraudProbability(), mlPrediction.isFraud()));
        }
        
        if (ruleResults != null) {
            long triggeredRules = ruleResults.stream().filter(RuleEvaluationResult::isTriggered).count();
            summary.append(String.format("Rules Triggered: %d of %d\n", triggeredRules, ruleResults.size()));
        }
        
        if (alerts != null && !alerts.isEmpty()) {
            summary.append(String.format("Alerts Generated: %d (Highest Priority: %s)\n", 
                alerts.size(), getHighestAlertPriority()));
        }
        
        if (recommendedActions != null && !recommendedActions.isEmpty()) {
            summary.append(String.format("Recommended Actions: %s\n", 
                recommendedActions.stream()
                    .map(action -> action.getActionType().toString())
                    .collect(Collectors.joining(", "))));
        }
        
        if (!decisionReasons.isEmpty()) {
            summary.append(String.format("Primary Reason: %s\n", getPrimaryDecisionReason()));
        }
        
        if (processingMetrics != null) {
            summary.append(String.format("Processing Time: %d ms (Efficiency: %.1f%%)\n", 
                processingMetrics.getTotalProcessingTimeMs(), getProcessingEfficiency() * 100));
        }
        
        summary.append(String.format("Analysis Time: %s\n", analysisTimestamp));
        summary.append(String.format("System Version: %s\n", systemVersion));
        
        return summary.toString();
    }
    
    /**
     * Create short summary for logging
     */
    public String createLogSummary() {
        return String.format("Analysis[%s]: %s - Risk: %.3f, Confidence: %.3f, Rules: %d triggered, Time: %dms",
            analysisId, decision, getTotalRiskScore(), confidence,
            ruleResults != null ? ruleResults.stream().mapToInt(r -> r.isTriggered() ? 1 : 0).sum() : 0,
            processingMetrics != null ? processingMetrics.getTotalProcessingTimeMs() : 0);
    }
    
    /**
     * Convert to fraud alert if high risk
     */
    public FraudAlert toFraudAlert() {
        if (decision == FraudDecision.APPROVE || getTotalRiskScore() < 0.5) {
            return null;
        }
        
        return FraudAlert.builder()
                .probability(getTotalRiskScore())
                .confidence(confidence)
                .riskLevel(mapToAlertRiskLevel())
                .alertLevel(mapToAlertLevel())
                .transactionId(transactionId)
                .userId(userId)
                .timestamp(analysisTimestamp)
                .modelUsed("Fraud Analysis System v" + systemVersion)
                .recommendedActions(recommendedActions != null ? 
                    recommendedActions.stream()
                        .map(action -> action.getDescription())
                        .collect(Collectors.toList()) : null)
                .build();
    }
    
    private MLPredictionResult.RiskLevel mapToAlertRiskLevel() {
        double score = getTotalRiskScore();
        if (score >= 0.8) return MLPredictionResult.RiskLevel.CRITICAL;
        if (score >= 0.6) return MLPredictionResult.RiskLevel.HIGH;
        if (score >= 0.4) return MLPredictionResult.RiskLevel.MEDIUM;
        if (score >= 0.2) return MLPredictionResult.RiskLevel.LOW;
        return MLPredictionResult.RiskLevel.MINIMAL;
    }
    
    private com.waqiti.common.fraud.model.AlertLevel mapToAlertLevel() {
        if (decision == FraudDecision.DECLINE) return com.waqiti.common.fraud.model.AlertLevel.CRITICAL;
        if (decision == FraudDecision.MANUAL_REVIEW) return com.waqiti.common.fraud.model.AlertLevel.HIGH;
        if (decision == FraudDecision.REVIEW) return com.waqiti.common.fraud.model.AlertLevel.MEDIUM;
        return com.waqiti.common.fraud.model.AlertLevel.LOW;
    }
    
    /**
     * Validate analysis result completeness
     */
    public boolean isComplete() {
        return analysisId != null &&
               transactionId != null &&
               decision != null &&
               confidence >= 0.0 &&
               qualityScore >= 0.0 &&
               analysisTimestamp != null;
    }
    
    /**
     * Get recommendations by priority
     */
    public List<RecommendedAction> getRecommendationsByPriority() {
        if (recommendedActions == null) {
            return List.of();
        }
        
        return recommendedActions.stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
    }
    
    // Supporting classes and enums
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedAction {
        private ActionType actionType;
        private String description;
        private int priority;
        private Map<String, Object> parameters;
        private boolean immediate;
        private String reason;
        
        public enum ActionType {
            APPROVE, DECLINE, REVIEW, MANUAL_REVIEW, REQUIRE_AUTH,
            BLOCK_USER, BLOCK_CARD, SEND_ALERT, CALL_CUSTOMER,
            INCREASE_MONITORING, DECREASE_LIMITS, FREEZE_ACCOUNT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingMetrics {
        private long totalProcessingTimeMs;
        private long mlProcessingTimeMs;
        private long rulesProcessingTimeMs;
        private long riskCalculationTimeMs;
        private long dataRetrievalTimeMs;
        private int totalRulesEvaluated;
        private int modelsUsed;
        private String performanceCategory;
        
        public double getEfficiencyScore() {
            if (totalProcessingTimeMs <= 100) return 1.0;
            if (totalProcessingTimeMs <= 500) return 0.8;
            if (totalProcessingTimeMs <= 1000) return 0.6;
            return 0.4;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessImpact {
        private double potentialLossAmount;
        private String impactCategory;
        private double customerSatisfactionImpact;
        private double operationalCostImpact;
        private String businessJustification;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceInfo {
        private List<String> applicableRegulations;
        private boolean requiresReporting;
        private Map<String, String> complianceStatus;
        private List<String> violations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalComparison {
        private double averageRiskScore;
        private double riskScorePercentile;
        private boolean unusualForUser;
        private String historicalPattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalValidation {
        private Map<String, String> thirdPartyResults;
        private boolean hasExternalAlerts;
        private double externalRiskScore;
        private String validationStatus;
    }
    
    public enum FraudDecision {
        APPROVE,        // Transaction should be approved
        DECLINE,        // Transaction should be declined
        REVIEW,         // Transaction requires automated review
        MANUAL_REVIEW,  // Transaction requires manual review
        REQUIRE_AUTH,   // Additional authentication required
        PENDING         // Decision pending additional data
    }
}