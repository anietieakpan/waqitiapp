package com.waqiti.common.fraud.scoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Risk factor calculation result for fraud scoring
 * 
 * Contains detailed analysis of individual risk factors and their
 * contributions to the overall fraud risk assessment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorResult {

    /**
     * Overall risk factor score (0.0 to 1.0)
     */
    private double overallRiskScore;

    /**
     * Confidence in the risk assessment (0.0 to 1.0)
     */
    private double confidence;

    /**
     * Individual risk factor scores
     */
    private List<IndividualRiskFactor> riskFactors;

    /**
     * Risk level classification
     */
    private RiskLevel riskLevel;

    /**
     * Calculation timestamp
     */
    private LocalDateTime calculatedAt;

    /**
     * Risk factor calculation metadata
     */
    private Map<String, Object> metadata;

    /**
     * High priority risk indicators
     */
    private List<String> criticalRiskFactors;

    /**
     * Rule violations that contributed to the score
     */
    private List<RuleViolation> ruleViolations;

    /**
     * Recommended actions based on risk factors
     */
    private List<String> recommendedActions;

    /**
     * Risk trend analysis
     */
    private RiskTrend riskTrend;

    public int getAccountAge() {
        return 0;
    }

    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndividualRiskFactor {
        private String factorName;
        private String factorCategory;
        private double score;
        private double weight;
        private double contribution;
        private String description;
        private String severity;
        private Map<String, Object> details;

        /**
         * Get risk level based on score
         */
        public String getRiskLevel() {
            if (severity != null) {
                return severity;
            }
            if (score >= 0.8) return "CRITICAL";
            if (score >= 0.6) return "HIGH";
            if (score >= 0.4) return "MEDIUM";
            if (score >= 0.2) return "LOW";
            return "MINIMAL";
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleViolation {
        private String ruleId;
        private String ruleName;
        private String violationType;
        private double violationScore;
        private String description;
        private LocalDateTime detectedAt;
        private Map<String, Object> violationDetails;
    }

    public enum RiskLevel {
        MINIMAL("Minimal Risk", 0.0, 0.2),
        LOW("Low Risk", 0.2, 0.4),
        MEDIUM("Medium Risk", 0.4, 0.6),
        HIGH("High Risk", 0.6, 0.8),
        CRITICAL("Critical Risk", 0.8, 1.0);

        private final String displayName;
        private final double minThreshold;
        private final double maxThreshold;

        RiskLevel(String displayName, double minThreshold, double maxThreshold) {
            this.displayName = displayName;
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }

        public String getDisplayName() { return displayName; }
        public double getMinThreshold() { return minThreshold; }
        public double getMaxThreshold() { return maxThreshold; }

        public static RiskLevel fromScore(double score) {
            for (RiskLevel level : values()) {
                if (score >= level.minThreshold && score < level.maxThreshold) {
                    return level;
                }
            }
            return CRITICAL;
        }
    }

    public enum RiskTrend {
        IMPROVING("Risk decreasing over time"),
        STABLE("Risk level stable"),
        DETERIORATING("Risk increasing over time"),
        VOLATILE("Risk level fluctuating");

        private final String description;

        RiskTrend(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    // Utility methods
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    public boolean hasCriticalFactors() {
        return criticalRiskFactors != null && !criticalRiskFactors.isEmpty();
    }

    public double getWeightedScore() {
        return overallRiskScore * confidence;
    }

    public String getSummary() {
        return String.format("Risk Level: %s (Score: %.2f, Confidence: %.2f)", 
                           riskLevel != null ? riskLevel.getDisplayName() : "Unknown",
                           overallRiskScore, confidence);
    }

    public List<String> getTopRiskFactors(int limit) {
        if (riskFactors == null) return List.of();

        return riskFactors.stream()
            .sorted((a, b) -> Double.compare(b.getContribution(), a.getContribution()))
            .limit(limit)
            .map(IndividualRiskFactor::getFactorName)
            .toList();
    }

    /**
     * Get amount risk level from risk factors
     */
    public com.waqiti.common.fraud.dto.AmountRiskLevel getAmountRiskLevel() {
        if (riskFactors == null) return com.waqiti.common.fraud.dto.AmountRiskLevel.LOW;

        double amountScore = riskFactors.stream()
            .filter(f -> "AMOUNT".equalsIgnoreCase(f.getFactorCategory()))
            .mapToDouble(IndividualRiskFactor::getScore)
            .max()
            .orElse(0.0);

        if (amountScore > 0.7) return com.waqiti.common.fraud.dto.AmountRiskLevel.HIGH;
        if (amountScore > 0.4) return com.waqiti.common.fraud.dto.AmountRiskLevel.MEDIUM;
        return com.waqiti.common.fraud.dto.AmountRiskLevel.LOW;
    }

    /**
     * Get velocity risk score
     */
    public double getVelocityScore() {
        if (riskFactors == null) return 0.0;

        return riskFactors.stream()
            .filter(f -> "VELOCITY".equalsIgnoreCase(f.getFactorCategory()))
            .mapToDouble(IndividualRiskFactor::getScore)
            .max()
            .orElse(0.0);
    }

    /**
     * Get geographic risk level
     */
    public com.waqiti.common.fraud.dto.GeographicRisk getGeographicRisk() {
        if (riskFactors == null) return com.waqiti.common.fraud.dto.GeographicRisk.LOW;

        double geoScore = riskFactors.stream()
            .filter(f -> "GEOGRAPHIC".equalsIgnoreCase(f.getFactorCategory()) ||
                         "LOCATION".equalsIgnoreCase(f.getFactorCategory()))
            .mapToDouble(IndividualRiskFactor::getScore)
            .max()
            .orElse(0.0);

        if (geoScore > 0.7) return com.waqiti.common.fraud.dto.GeographicRisk.HIGH;
        if (geoScore > 0.4) return com.waqiti.common.fraud.dto.GeographicRisk.MEDIUM;
        return com.waqiti.common.fraud.dto.GeographicRisk.LOW;
    }

    /**
     * Get device risk level
     */
    public com.waqiti.common.fraud.dto.DeviceRisk getDeviceRisk() {
        if (riskFactors == null) return com.waqiti.common.fraud.dto.DeviceRisk.LOW;

        double deviceScore = riskFactors.stream()
            .filter(f -> "DEVICE".equalsIgnoreCase(f.getFactorCategory()))
            .mapToDouble(IndividualRiskFactor::getScore)
            .max()
            .orElse(0.0);

        if (deviceScore > 0.7) return com.waqiti.common.fraud.dto.DeviceRisk.HIGH;
        if (deviceScore > 0.4) return com.waqiti.common.fraud.dto.DeviceRisk.MEDIUM;
        return com.waqiti.common.fraud.dto.DeviceRisk.LOW;
    }

    /**
     * Get time-based risk level
     */
    public TimeRisk getTimeRisk() {
        if (riskFactors == null) return TimeRisk.LOW;

        double timeScore = riskFactors.stream()
            .filter(f -> "TIME".equalsIgnoreCase(f.getFactorCategory()) ||
                         "TEMPORAL".equalsIgnoreCase(f.getFactorCategory()))
            .mapToDouble(IndividualRiskFactor::getScore)
            .max()
            .orElse(0.0);

        if (timeScore > 0.7) return TimeRisk.HIGH;
        if (timeScore > 0.4) return TimeRisk.MEDIUM;
        return TimeRisk.LOW;
    }

    /**
     * Time-based risk enum
     */
    public enum TimeRisk {
        LOW, MEDIUM, HIGH
    }
}