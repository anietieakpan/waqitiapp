package com.waqiti.security.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud Detection Result
 * 
 * Comprehensive result from fraud detection analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionResult {

    private UUID analysisId;
    private UUID transactionId;
    private UUID userId;
    
    private FraudRiskLevel riskLevel;
    private BigDecimal riskScore; // 0-100 scale
    private String decision; // APPROVE, DECLINE, REVIEW
    
    private List<FraudIndicator> indicators;
    private Map<String, Object> features;
    private Map<String, BigDecimal> scores;
    
    private LocalDateTime analysisTimestamp;
    private String modelVersion;
    private Long processingTimeMs;
    
    private boolean requiresManualReview;
    private String reviewReason;
    private List<String> recommendations;

    public enum FraudRiskLevel {
        VERY_LOW("Very Low Risk", 0, 10),
        LOW("Low Risk", 11, 25),
        MEDIUM("Medium Risk", 26, 50),
        HIGH("High Risk", 51, 75),
        VERY_HIGH("Very High Risk", 76, 90),
        CRITICAL("Critical Risk", 91, 100);

        private final String description;
        private final int minScore;
        private final int maxScore;

        FraudRiskLevel(String description, int minScore, int maxScore) {
            this.description = description;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }

        public static FraudRiskLevel fromScore(BigDecimal score) {
            int scoreInt = score.intValue();
            for (FraudRiskLevel level : values()) {
                if (scoreInt >= level.minScore && scoreInt <= level.maxScore) {
                    return level;
                }
            }
            return CRITICAL;
        }

        public String getDescription() { return description; }
        public int getMinScore() { return minScore; }
        public int getMaxScore() { return maxScore; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudIndicator {
        private String type;
        private String name;
        private String description;
        private BigDecimal severity; // 0-1 scale
        private BigDecimal confidence; // 0-1 scale
        private Map<String, Object> details;
        private boolean isBlocking;
    }

    public boolean isHighRisk() {
        return riskLevel == FraudRiskLevel.HIGH || 
               riskLevel == FraudRiskLevel.VERY_HIGH || 
               riskLevel == FraudRiskLevel.CRITICAL;
    }

    public boolean shouldBlock() {
        return indicators.stream().anyMatch(FraudIndicator::isBlocking) ||
               riskLevel == FraudRiskLevel.CRITICAL;
    }

    public boolean shouldReview() {
        return requiresManualReview || 
               riskLevel == FraudRiskLevel.HIGH || 
               riskLevel == FraudRiskLevel.VERY_HIGH;
    }
}