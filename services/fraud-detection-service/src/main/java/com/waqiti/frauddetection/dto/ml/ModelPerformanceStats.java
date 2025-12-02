package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Model Performance Statistics DTO
 *
 * Real-time and historical performance metrics for deployed fraud detection models.
 * Tracks accuracy, latency, throughput, and business impact metrics.
 *
 * PRODUCTION-GRADE DTO
 * - Comprehensive performance metrics
 * - Temporal tracking (hourly, daily, weekly)
 * - SLA compliance monitoring
 * - Drift detection indicators
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPerformanceStats {

    private String modelVersion;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    /**
     * Volume metrics
     */
    private Long totalPredictions;
    private Long fraudPredictions;
    private Long legitimatePredictions;

    /**
     * Accuracy metrics (requires ground truth feedback)
     */
    private Long truePositives; // Correctly identified fraud
    private Long trueNegatives; // Correctly identified legitimate
    private Long falsePositives; // Incorrectly flagged as fraud
    private Long falseNegatives; // Missed fraud

    /**
     * Performance metrics
     */
    private Double avgLatencyMs;
    private Double p50LatencyMs;
    private Double p95LatencyMs;
    private Double p99LatencyMs;
    private Long maxLatencyMs;

    /**
     * Throughput
     */
    private Double predictionsPerSecond;
    private Double predictionsPerMinute;

    /**
     * Business impact metrics
     */
    private Double estimatedFraudPrevented; // $ amount
    private Double estimatedFalsePositiveCost; // $ amount
    private Integer manualReviewsTriggered;

    /**
     * Model health indicators
     */
    private Double avgConfidence;
    private Double predictionDriftScore; // 0.0 - 1.0
    private Double featureDriftScore; // 0.0 - 1.0

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Calculate precision
     */
    public Double getPrecision() {
        if (truePositives == null || falsePositives == null) {
            return null;
        }
        long positives = truePositives + falsePositives;
        return positives > 0 ? (double) truePositives / positives : 0.0;
    }

    /**
     * Calculate recall
     */
    public Double getRecall() {
        if (truePositives == null || falseNegatives == null) {
            return null;
        }
        long actualFraud = truePositives + falseNegatives;
        return actualFraud > 0 ? (double) truePositives / actualFraud : 0.0;
    }

    /**
     * Calculate F1 score
     */
    public Double getF1Score() {
        Double precision = getPrecision();
        Double recall = getRecall();
        if (precision == null || recall == null) {
            return null;
        }
        double sum = precision + recall;
        return sum > 0 ? 2 * (precision * recall) / sum : 0.0;
    }

    /**
     * Calculate accuracy
     */
    public Double getAccuracy() {
        if (truePositives == null || trueNegatives == null ||
            falsePositives == null || falseNegatives == null) {
            return null;
        }
        long total = truePositives + trueNegatives + falsePositives + falseNegatives;
        return total > 0 ? (double) (truePositives + trueNegatives) / total : 0.0;
    }

    /**
     * Calculate false positive rate
     */
    public Double getFalsePositiveRate() {
        if (falsePositives == null || trueNegatives == null) {
            return null;
        }
        long actualLegitimate = falsePositives + trueNegatives;
        return actualLegitimate > 0 ? (double) falsePositives / actualLegitimate : 0.0;
    }

    /**
     * Check if model is meeting SLA (p95 latency < 100ms)
     */
    public boolean meetsSLA() {
        return p95LatencyMs != null && p95LatencyMs < 100.0;
    }

    /**
     * Check if model drift detected
     */
    public boolean hasDrift() {
        return (predictionDriftScore != null && predictionDriftScore > 0.1) ||
               (featureDriftScore != null && featureDriftScore > 0.1);
    }
}
