package com.waqiti.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Risk Metrics Entity
 *
 * Aggregated risk metrics for analytics and monitoring including:
 * - Risk score distributions
 * - Assessment statistics
 * - Rule performance
 * - Trend analysis
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "risk_metrics")
@CompoundIndexes({
    @CompoundIndex(name = "period_idx", def = "{'periodStart': 1, 'periodEnd': 1, 'metricType': 1}")
})
public class RiskMetrics {

    @Id
    private String id;

    @Indexed
    private String metricType; // HOURLY, DAILY, WEEKLY, MONTHLY, REAL_TIME

    @Indexed
    private String entityType; // GLOBAL, USER, MERCHANT, DEVICE

    private String entityId; // null for GLOBAL

    // Time Period
    @Indexed
    private LocalDateTime periodStart;

    @Indexed
    private LocalDateTime periodEnd;

    // Assessment Metrics
    @Builder.Default
    private Long totalAssessments = 0L;

    @Builder.Default
    private Long lowRiskCount = 0L;

    @Builder.Default
    private Long mediumRiskCount = 0L;

    @Builder.Default
    private Long highRiskCount = 0L;

    @Builder.Default
    private Long criticalRiskCount = 0L;

    // Decision Metrics
    @Builder.Default
    private Long approvedCount = 0L;

    @Builder.Default
    private Long blockedCount = 0L;

    @Builder.Default
    private Long reviewCount = 0L;

    @Builder.Default
    private Long requiresMfaCount = 0L;

    // Score Statistics
    private Double averageRiskScore;
    private Double medianRiskScore;
    private Double minRiskScore;
    private Double maxRiskScore;
    private Double stdDevRiskScore;

    // Score Distribution (percentiles)
    private Double p25RiskScore; // 25th percentile
    private Double p50RiskScore; // 50th percentile (median)
    private Double p75RiskScore; // 75th percentile
    private Double p90RiskScore; // 90th percentile
    private Double p95RiskScore; // 95th percentile
    private Double p99RiskScore; // 99th percentile

    // ML Model Metrics
    private Double averageMlScore;
    private Double mlConfidenceAverage;
    @Builder.Default
    private Long mlPredictionsCount = 0L;
    private String mlModelVersion;

    // Rule Engine Metrics
    private Double averageRulesTriggered;
    @Builder.Default
    private Long totalRulesTriggered = 0L;
    private Map<String, Long> ruleTriggeredCounts; // ruleName -> count
    private Map<String, Double> ruleEffectivenessScores;

    // Factor Metrics
    private Map<String, Double> averageFactorScores; // factorName -> avgScore
    private Map<String, Long> factorTriggerCounts;

    // Processing Metrics
    private Long averageProcessingTimeMs;
    private Long minProcessingTimeMs;
    private Long maxProcessingTimeMs;
    private Long p95ProcessingTimeMs;

    // Transaction Metrics
    @Builder.Default
    private BigDecimal totalTransactionAmount = BigDecimal.ZERO;

    private BigDecimal averageTransactionAmount;
    private BigDecimal blockedTransactionAmount;
    private BigDecimal approvedTransactionAmount;

    // Velocity Metrics
    private Double averageVelocityScore;
    @Builder.Default
    private Long velocityViolations = 0L;

    // Geographic Metrics
    @Builder.Default
    private Integer uniqueCountries = 0;

    private Map<String, Long> countryDistribution;
    @Builder.Default
    private Long vpnDetections = 0L;

    @Builder.Default
    private Long impossibleTravelDetections = 0L;

    // Device Metrics
    @Builder.Default
    private Integer uniqueDevices = 0;

    @Builder.Default
    private Long rootedDeviceDetections = 0L;

    @Builder.Default
    private Long emulatorDetections = 0L;

    // Pattern Detection Metrics
    @Builder.Default
    private Long cardTestingDetections = 0L;

    @Builder.Default
    private Long rapidFireDetections = 0L;

    @Builder.Default
    private Long anomalyDetections = 0L;

    private Map<String, Long> patternDetectionCounts;

    // False Positive/Negative Tracking
    @Builder.Default
    private Long falsePositives = 0L;

    @Builder.Default
    private Long falseNegatives = 0L;

    @Builder.Default
    private Long confirmedFrauds = 0L;

    private Double falsePositiveRate;
    private Double falseNegativeRate;

    // Merchant Metrics (if applicable)
    private Map<String, Long> merchantRiskDistribution;
    private Map<String, Double> merchantCategoryRiskScores;

    // Threshold Violations
    @Builder.Default
    private Long thresholdViolations = 0L;

    private Map<String, Long> violatedThresholds;

    // Trend Indicators
    private Double riskScoreTrend; // Positive = increasing risk
    private Double volumeTrend;
    private Double blockRateTrend;

    // Comparison to Previous Period
    private Double riskScoreChange; // Percentage change
    private Long assessmentCountChange;
    private Double blockRateChange;

    // System Performance
    private Double systemAvailability; // Percentage
    private Long errorCount;
    private Long circuitBreakerActivations;

    // Audit Fields
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime lastAggregatedAt;

    // Metadata
    private Map<String, Object> metadata;

    /**
     * Calculate block rate
     */
    public Double getBlockRate() {
        if (totalAssessments == 0) {
            return 0.0;
        }
        return (double) blockedCount / totalAssessments;
    }

    /**
     * Calculate approval rate
     */
    public Double getApprovalRate() {
        if (totalAssessments == 0) {
            return 0.0;
        }
        return (double) approvedCount / totalAssessments;
    }

    /**
     * Calculate review rate
     */
    public Double getReviewRate() {
        if (totalAssessments == 0) {
            return 0.0;
        }
        return (double) reviewCount / totalAssessments;
    }

    /**
     * Calculate high risk rate
     */
    public Double getHighRiskRate() {
        if (totalAssessments == 0) {
            return 0.0;
        }
        return (double) (highRiskCount + criticalRiskCount) / totalAssessments;
    }

    /**
     * Update with new assessment
     */
    public void addAssessment(Double riskScore, String riskLevel, String decision,
                             BigDecimal amount, Long processingTime) {
        this.totalAssessments++;

        // Update risk level counts
        switch (riskLevel) {
            case "LOW":
                this.lowRiskCount++;
                break;
            case "MEDIUM":
                this.mediumRiskCount++;
                break;
            case "HIGH":
                this.highRiskCount++;
                break;
            case "CRITICAL":
                this.criticalRiskCount++;
                break;
        }

        // Update decision counts
        switch (decision) {
            case "APPROVE":
                this.approvedCount++;
                if (amount != null) {
                    this.approvedTransactionAmount =
                        (approvedTransactionAmount != null ? approvedTransactionAmount : BigDecimal.ZERO)
                            .add(amount);
                }
                break;
            case "BLOCK":
                this.blockedCount++;
                if (amount != null) {
                    this.blockedTransactionAmount =
                        (blockedTransactionAmount != null ? blockedTransactionAmount : BigDecimal.ZERO)
                            .add(amount);
                }
                break;
            case "REVIEW":
                this.reviewCount++;
                break;
            case "REQUIRE_MFA":
                this.requiresMfaCount++;
                break;
        }

        // Update amounts
        if (amount != null) {
            this.totalTransactionAmount =
                (totalTransactionAmount != null ? totalTransactionAmount : BigDecimal.ZERO)
                    .add(amount);
        }

        // Update processing time stats
        if (processingTime != null) {
            if (averageProcessingTimeMs == null) {
                averageProcessingTimeMs = processingTime;
            } else {
                averageProcessingTimeMs =
                    (averageProcessingTimeMs + processingTime) / 2;
            }

            if (minProcessingTimeMs == null || processingTime < minProcessingTimeMs) {
                minProcessingTimeMs = processingTime;
            }

            if (maxProcessingTimeMs == null || processingTime > maxProcessingTimeMs) {
                maxProcessingTimeMs = processingTime;
            }
        }

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate false positive rate
     */
    public void calculateFalsePositiveRate() {
        long totalPositives = blockedCount + reviewCount;
        if (totalPositives > 0) {
            this.falsePositiveRate = (double) falsePositives / totalPositives;
        }
    }

    /**
     * Calculate false negative rate
     */
    public void calculateFalseNegativeRate() {
        long totalNegatives = approvedCount;
        if (totalNegatives > 0) {
            this.falseNegativeRate = (double) falseNegatives / totalNegatives;
        }
    }
}
