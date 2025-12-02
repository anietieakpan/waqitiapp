package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for fraud score queries.
 * Provides detailed fraud risk scoring information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScoreResponse {

    private UUID transactionId;
    private UUID userId;
    
    // Core scoring
    private Double overallScore; // 0-100 scale
    private String riskCategory; // LOW, MEDIUM, HIGH, CRITICAL
    private Double confidence; // Confidence in the score (0-1)
    
    // Score components
    private ScoreComponents components;
    
    // Historical scoring
    private Double previousScore;
    private Double scoreChange;
    private String scoreTrend; // INCREASING, STABLE, DECREASING
    
    // Score factors
    private List<ScoreFactor> positiveFactors;
    private List<ScoreFactor> negativeFactors;
    private Map<String, Double> categoryScores;
    
    // ML Model information
    private String modelVersion;
    private LocalDateTime modelUpdateDate;
    private Map<String, Double> featureContributions;
    
    // Thresholds
    private Double approvalThreshold;
    private Double reviewThreshold;
    private Double declineThreshold;
    
    // Recommendations
    private List<String> riskMitigationSuggestions;
    private Boolean requiresEnhancedMonitoring;
    private Integer recommendedReviewPeriodDays;
    
    // Metadata
    private LocalDateTime scoreCalculationTime;
    private Long calculationTimeMs;
    private String scoringEngine;
    private Map<String, Object> additionalMetadata;

    /**
     * Detailed score components
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreComponents {
        private Double behavioralScore;
        private Double velocityScore;
        private Double deviceScore;
        private Double locationScore;
        private Double networkScore;
        private Double transactionPatternScore;
        private Double paymentMethodScore;
        private Double merchantScore;
        private Double timeBasedScore;
        private Double amountBasedScore;
        
        /**
         * Calculates weighted average of all component scores
         */
        public Double getWeightedAverage() {
            double sum = 0;
            int count = 0;
            
            if (behavioralScore != null) { sum += behavioralScore * 1.5; count++; }
            if (velocityScore != null) { sum += velocityScore * 1.3; count++; }
            if (deviceScore != null) { sum += deviceScore * 1.2; count++; }
            if (locationScore != null) { sum += locationScore * 1.1; count++; }
            if (networkScore != null) { sum += networkScore * 1.4; count++; }
            if (transactionPatternScore != null) { sum += transactionPatternScore * 1.3; count++; }
            if (paymentMethodScore != null) { sum += paymentMethodScore; count++; }
            if (merchantScore != null) { sum += merchantScore; count++; }
            if (timeBasedScore != null) { sum += timeBasedScore * 0.8; count++; }
            if (amountBasedScore != null) { sum += amountBasedScore * 1.2; count++; }
            
            return count > 0 ? sum / (count * 1.15) : 0.0; // 1.15 is average weight
        }
    }

    /**
     * Individual score factor
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreFactor {
        private String factorName;
        private String description;
        private Double impact; // Impact on score (-100 to +100)
        private String category;
        private String severity; // LOW, MEDIUM, HIGH
        private Map<String, Object> details;
        
        public boolean isPositive() {
            return impact != null && impact > 0;
        }
        
        public boolean isNegative() {
            return impact != null && impact < 0;
        }
    }

    /**
     * Determines if the score indicates low risk
     */
    public boolean isLowRisk() {
        return overallScore != null && overallScore < 30;
    }

    /**
     * Determines if the score indicates medium risk
     */
    public boolean isMediumRisk() {
        return overallScore != null && overallScore >= 30 && overallScore < 60;
    }

    /**
     * Determines if the score indicates high risk
     */
    public boolean isHighRisk() {
        return overallScore != null && overallScore >= 60 && overallScore < 80;
    }

    /**
     * Determines if the score indicates critical risk
     */
    public boolean isCriticalRisk() {
        return overallScore != null && overallScore >= 80;
    }

    /**
     * Gets decision based on score and thresholds
     */
    public String getDecision() {
        if (overallScore == null) return "UNKNOWN";
        
        if (approvalThreshold != null && overallScore < approvalThreshold) {
            return "APPROVE";
        } else if (declineThreshold != null && overallScore >= declineThreshold) {
            return "DECLINE";
        } else if (reviewThreshold != null && overallScore >= reviewThreshold) {
            return "REVIEW";
        }
        
        return "REVIEW"; // Default to review if thresholds not set
    }

    /**
     * Calculates the risk level change
     */
    public String getRiskLevelChange() {
        if (previousScore == null || scoreChange == null) return "UNKNOWN";
        
        double changePercent = (scoreChange / previousScore) * 100;
        
        if (Math.abs(changePercent) < 5) return "STABLE";
        if (changePercent > 20) return "SIGNIFICANTLY_INCREASED";
        if (changePercent > 5) return "INCREASED";
        if (changePercent < -20) return "SIGNIFICANTLY_DECREASED";
        if (changePercent < -5) return "DECREASED";
        
        return "STABLE";
    }
}