package com.waqiti.frauddetection.dto.ml;

import com.waqiti.frauddetection.dto.RiskLevel;
import lombok.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fraud Risk Score DTO
 *
 * Comprehensive risk scoring result combining ML models, rule engines,
 * and manual risk factors. Provides detailed breakdown of risk components
 * for transparency and explainability.
 *
 * PRODUCTION-GRADE DTO
 * - Multi-component risk scoring (ML + Rules + Manual)
 * - Risk factor decomposition
 * - Audit trail support
 * - Threshold-based decision support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskScore {

    /**
     * Unique score ID for tracking
     */
    @NotNull
    private String scoreId;

    /**
     * Transaction/Entity being scored
     */
    @NotNull
    private String transactionId;

    private String userId;

    /**
     * Final Risk Score (0.0 - 1.0)
     * Normalized composite score
     */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double finalScore;

    /**
     * Risk Level classification
     */
    @NotNull
    private RiskLevel riskLevel;

    /**
     * Component Scores (weighted components)
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double mlModelScore; // ML model prediction

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double ruleEngineScore; // Rule-based score

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double manualRiskScore; // Manual risk adjustments

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double behaviorScore; // User behavior analysis

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double deviceScore; // Device risk score

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double locationScore; // Geographic risk score

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double velocityScore; // Transaction velocity score

    /**
     * Component Weights (sum to 1.0)
     */
    @Builder.Default
    private Map<String, Double> componentWeights = new HashMap<>();

    /**
     * Risk Factors (individual risk indicators)
     */
    @Builder.Default
    private List<RiskFactor> riskFactors = new ArrayList<>();

    /**
     * Triggered rules count
     */
    @Builder.Default
    private Integer triggeredRulesCount = 0;

    @Builder.Default
    private List<String> triggeredRuleIds = new ArrayList<>();

    /**
     * Score metadata
     */
    private LocalDateTime scoredAt;

    private String scoringEngine; // e.g., "hybrid-v2.0"

    private Long scoringTimeMs;

    /**
     * Confidence in score (0.0 - 1.0)
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confidence;

    /**
     * Previous score (for trend analysis)
     */
    private Double previousScore;

    private LocalDateTime previousScoredAt;

    /**
     * Score change indicators
     */
    public Double getScoreChange() {
        if (previousScore == null) {
            return null;
        }
        return finalScore - previousScore;
    }

    public boolean isScoreIncreasing() {
        Double change = getScoreChange();
        return change != null && change > 0;
    }

    public boolean isScoreDecreasing() {
        Double change = getScoreChange();
        return change != null && change < 0;
    }

    /**
     * Get score as percentage (0-100)
     */
    public double getScorePercentage() {
        return finalScore * 100.0;
    }

    /**
     * Check if score exceeds threshold
     */
    public boolean exceedsThreshold(double threshold) {
        return finalScore >= threshold;
    }

    /**
     * Get high-severity risk factors
     */
    public List<RiskFactor> getHighSeverityFactors() {
        return riskFactors.stream()
            .filter(RiskFactor::isHighSeverity)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get critical risk factors
     */
    public List<RiskFactor> getCriticalFactors() {
        return riskFactors.stream()
            .filter(RiskFactor::isCritical)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Check if has critical risk factors
     */
    public boolean hasCriticalFactors() {
        return riskFactors.stream().anyMatch(RiskFactor::isCritical);
    }

    /**
     * Get top N risk factors by impact
     */
    public List<RiskFactor> getTopRiskFactors(int n) {
        return riskFactors.stream()
            .sorted((f1, f2) -> Double.compare(f2.getImpact(), f1.getImpact()))
            .limit(n)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate component contribution to final score
     */
    public Map<String, Double> getComponentContributions() {
        Map<String, Double> contributions = new HashMap<>();

        if (mlModelScore != null && componentWeights.containsKey("ml_model")) {
            contributions.put("ml_model", mlModelScore * componentWeights.get("ml_model"));
        }
        if (ruleEngineScore != null && componentWeights.containsKey("rules")) {
            contributions.put("rules", ruleEngineScore * componentWeights.get("rules"));
        }
        if (behaviorScore != null && componentWeights.containsKey("behavior")) {
            contributions.put("behavior", behaviorScore * componentWeights.get("behavior"));
        }
        if (deviceScore != null && componentWeights.containsKey("device")) {
            contributions.put("device", deviceScore * componentWeights.get("device"));
        }
        if (locationScore != null && componentWeights.containsKey("location")) {
            contributions.put("location", locationScore * componentWeights.get("location"));
        }

        return contributions;
    }

    /**
     * Add risk factor
     */
    public void addRiskFactor(RiskFactor factor) {
        if (this.riskFactors == null) {
            this.riskFactors = new ArrayList<>();
        }
        this.riskFactors.add(factor);
    }

    /**
     * Risk Factor Inner Class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorId;
        private String factorName;
        private String description;
        private Double impact; // Impact on score (0.0 - 1.0)
        private String severity; // "low", "medium", "high", "critical"
        private String category; // "behavior", "device", "location", "velocity", etc.
        private Map<String, Object> details;

        public boolean isHighSeverity() {
            return "high".equalsIgnoreCase(severity);
        }

        public boolean isCritical() {
            return "critical".equalsIgnoreCase(severity);
        }

        public boolean hasHighImpact() {
            return impact != null && impact >= 0.7;
        }
    }

    /**
     * Create summary for logging
     */
    public String getSummary() {
        return String.format(
            "RiskScore[id=%s, score=%.2f%%, level=%s, factors=%d, rules=%d, confidence=%.2f]",
            scoreId,
            getScorePercentage(),
            riskLevel,
            riskFactors != null ? riskFactors.size() : 0,
            triggeredRulesCount,
            confidence
        );
    }
}
