package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Monitoring Result
 * 
 * Result of transaction monitoring analysis for fraud detection.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringResult {
    
    /**
     * Monitoring session ID
     */
    private String monitoringId;
    
    /**
     * Transaction ID that was monitored
     */
    private String transactionId;
    
    /**
     * Overall risk score (0.0 to 1.0)
     */
    private Double overallRiskScore;
    
    /**
     * Risk level assessment
     */
    private RiskLevel riskLevel;
    
    /**
     * Monitoring status
     */
    private MonitoringStatus status;
    
    /**
     * Detected patterns
     */
    private List<DetectedPattern> detectedPatterns;
    
    /**
     * Triggered alerts
     */
    private List<Alert> alerts;
    
    /**
     * Velocity check results
     */
    private VelocityCheckResults velocityResults;
    
    /**
     * Behavioral analysis results
     */
    private BehavioralAnalysisResult behavioralAnalysis;
    
    /**
     * ML model predictions
     */
    private List<MLPrediction> mlPredictions;
    
    /**
     * Rule engine results
     */
    private List<RuleResult> ruleResults;
    
    /**
     * Recommendation for action
     */
    private Recommendation recommendation;
    
    /**
     * Analysis duration in milliseconds
     */
    private Long analysisDurationMs;
    
    /**
     * Analysis timestamp
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Confidence level in the analysis
     */
    private Double confidence;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Reasons for the risk assessment
     */
    private List<String> riskReasons;
    
    /**
     * Follow-up actions required
     */
    private List<FollowUpAction> followUpActions;
    
    public enum RiskLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        CRITICAL
    }
    
    public enum MonitoringStatus {
        COMPLETED,
        PARTIAL,
        FAILED,
        TIMEOUT,
        CANCELLED
    }
    
    public enum Recommendation {
        APPROVE,
        REVIEW,
        DECLINE,
        HOLD,
        INVESTIGATE,
        REQUEST_ADDITIONAL_INFO
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedPattern {
        private TransactionMonitoringRequest.PatternType patternType;
        private String patternName;
        private Double confidence;
        private String description;
        private Map<String, Object> patternData;
        private LocalDateTime detectedAt;
        private String severity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private String alertId;
        private String alertType;
        private String severity;
        private String title;
        private String description;
        private Double riskScore;
        private LocalDateTime triggeredAt;
        private Map<String, Object> alertData;
        private Boolean requiresImmedateAction;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityCheckResults {
        private Boolean velocityLimitExceeded;
        private String velocityType;
        private Integer currentCount;
        private Integer limitCount;
        private String timeWindow;
        private Double velocityScore;
        private List<VelocityViolation> violations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityViolation {
        private String violationType;
        private String description;
        private Integer actualValue;
        private Integer limitValue;
        private Double severity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralAnalysisResult {
        private Double behavioralScore;
        private List<String> anomalies;
        private UserProfile normalProfile;
        private UserProfile currentProfile;
        private Double deviationScore;
        private List<BehavioralFlag> flags;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfile {
        private String userId;
        private Map<String, Object> characteristics;
        private LocalDateTime profileDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralFlag {
        private String flagType;
        private String description;
        private Double severity;
        private Map<String, Object> evidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLPrediction {
        private String modelId;
        private String modelName;
        private Double prediction;
        private Double confidence;
        private Map<String, Double> featureImportance;
        private String explanation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleResult {
        private String ruleId;
        private String ruleName;
        private Boolean triggered;
        private Double score;
        private String explanation;
        private Map<String, Object> ruleData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowUpAction {
        private String actionType;
        private String description;
        private String priority;
        private LocalDateTime dueDate;
        private String assignedTo;
        private Map<String, Object> actionData;
    }
    
    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlock() {
        return recommendation == Recommendation.DECLINE || 
               riskLevel == RiskLevel.CRITICAL ||
               (riskLevel == RiskLevel.VERY_HIGH && overallRiskScore >= 0.9);
    }
    
    /**
     * Check if manual review is needed
     */
    public boolean needsManualReview() {
        return recommendation == Recommendation.REVIEW ||
               recommendation == Recommendation.INVESTIGATE ||
               riskLevel == RiskLevel.HIGH ||
               (alerts != null && alerts.stream().anyMatch(a -> a.getRequiresImmedateAction()));
    }
    
    /**
     * Get highest severity alert
     */
    public Alert getHighestSeverityAlert() {
        if (alerts == null || alerts.isEmpty()) {
            return null;
        }
        
        return alerts.stream()
                .max((a1, a2) -> {
                    String[] severityOrder = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
                    int s1 = java.util.Arrays.asList(severityOrder).indexOf(a1.getSeverity());
                    int s2 = java.util.Arrays.asList(severityOrder).indexOf(a2.getSeverity());
                    return Integer.compare(s1, s2);
                })
                .orElse(null);
    }
    
    /**
     * Get summary of detected patterns
     */
    public String getPatternSummary() {
        if (detectedPatterns == null || detectedPatterns.isEmpty()) {
            return "No patterns detected";
        }
        
        return detectedPatterns.stream()
                .map(DetectedPattern::getPatternName)
                .reduce((p1, p2) -> p1 + ", " + p2)
                .orElse("Unknown patterns");
    }
}