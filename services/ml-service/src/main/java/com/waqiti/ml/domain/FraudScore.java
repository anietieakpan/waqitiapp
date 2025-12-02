package com.waqiti.ml.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fraud Score Domain Model
 * 
 * Represents the comprehensive fraud assessment result
 * from multiple ML models and rule engines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScore {
    
    private String transactionId;
    private String userId;
    private double overallScore;
    private FraudRiskLevel riskLevel;
    private String decision;
    private Instant timestamp;
    
    // Individual model scores
    private double neuralNetworkScore;
    private double xgboostScore;
    private double isolationForestScore;
    private double ruleEngineScore;
    private double behaviorAnalysisScore;
    private double networkAnalysisScore;
    
    // Feature contributions
    private Map<String, Double> featureImportance;
    
    // Risk factors identified
    private List<RiskFactor> riskFactors;
    
    // Model metadata
    private String modelVersion;
    private double confidence;
    private long processingTimeMs;
    
    // Business context
    private String businessOperation;
    private Map<String, Object> businessContext;
    
    // Recommendations
    private List<String> recommendations;
    private boolean requiresManualReview;
    private String escalationReason;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String type;
        private String description;
        private double severity;
        private String category;
        private Map<String, Object> details;
    }
    
    public enum FraudRiskLevel {
        VERY_LOW(0.0, 0.1, "APPROVE"),
        LOW(0.1, 0.3, "APPROVE"),
        MEDIUM(0.3, 0.7, "REVIEW"),
        HIGH(0.7, 0.9, "DECLINE"),
        VERY_HIGH(0.9, 1.0, "DECLINE");
        
        private final double minScore;
        private final double maxScore;
        private final String defaultAction;
        
        FraudRiskLevel(double minScore, double maxScore, String defaultAction) {
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.defaultAction = defaultAction;
        }
        
        public static FraudRiskLevel fromScore(double score) {
            for (FraudRiskLevel level : values()) {
                if (score >= level.minScore && score < level.maxScore) {
                    return level;
                }
            }
            return VERY_HIGH;
        }
        
        public double getMinScore() { return minScore; }
        public double getMaxScore() { return maxScore; }
        public String getDefaultAction() { return defaultAction; }
    }
}