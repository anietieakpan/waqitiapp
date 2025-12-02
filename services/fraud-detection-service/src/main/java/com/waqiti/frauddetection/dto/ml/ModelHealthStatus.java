package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model Health Status DTO
 *
 * Comprehensive health check status for deployed ML models.
 * Monitors model availability, performance, data quality, and drift.
 *
 * PRODUCTION-GRADE DTO
 * - Multi-dimensional health checks
 * - Alert thresholds
 * - Diagnostic information
 * - Remediation recommendations
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelHealthStatus {

    private String modelVersion;
    private LocalDateTime checkedAt;

    /**
     * Overall health status
     */
    private HealthState overallHealth; // HEALTHY, DEGRADED, UNHEALTHY, CRITICAL

    /**
     * Individual health components
     */
    private HealthState availabilityHealth;
    private HealthState performanceHealth;
    private HealthState accuracyHealth;
    private HealthState dataQualityHealth;
    private HealthState driftHealth;

    /**
     * Availability metrics
     */
    private Double uptimePercentage;
    private Integer consecutiveFailures;
    private LocalDateTime lastSuccessfulPrediction;

    /**
     * Performance metrics
     */
    private Double currentP95LatencyMs;
    private Double slaThresholdMs;
    private Boolean meetingSLA;

    /**
     * Accuracy metrics (from recent feedback)
     */
    private Double recentAccuracy;
    private Double recentPrecision;
    private Double recentRecall;
    private Integer feedbackSampleSize;

    /**
     * Drift detection
     */
    private Double predictionDriftScore; // 0.0 - 1.0
    private Double featureDriftScore; // 0.0 - 1.0
    private Boolean driftDetected;

    /**
     * Data quality
     */
    private Double missingFeatureRate;
    private Double invalidFeatureRate;
    private Boolean dataQualityIssues;

    /**
     * Alerts and warnings
     */
    @Builder.Default
    private List<HealthAlert> alerts = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Recommendations
     */
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    /**
     * Health State Enum
     */
    public enum HealthState {
        HEALTHY,    // All metrics within normal range
        DEGRADED,   // Some metrics outside normal but still functional
        UNHEALTHY,  // Multiple metrics critical, may need intervention
        CRITICAL    // Severe issues, immediate action required
    }

    /**
     * Health Alert Inner Class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthAlert {
        private String alertType; // "availability", "performance", "accuracy", "drift", "data_quality"
        private String severity; // "warning", "error", "critical"
        private String message;
        private Double value;
        private Double threshold;
        private LocalDateTime triggeredAt;
    }

    /**
     * Check if model is healthy
     */
    public boolean isHealthy() {
        return overallHealth == HealthState.HEALTHY;
    }

    /**
     * Check if model needs attention
     */
    public boolean needsAttention() {
        return overallHealth == HealthState.DEGRADED ||
               overallHealth == HealthState.UNHEALTHY ||
               overallHealth == HealthState.CRITICAL;
    }

    /**
     * Check if immediate action required
     */
    public boolean requiresImmediateAction() {
        return overallHealth == HealthState.CRITICAL;
    }

    /**
     * Get critical alerts
     */
    public List<HealthAlert> getCriticalAlerts() {
        return alerts.stream()
            .filter(a -> "critical".equalsIgnoreCase(a.getSeverity()))
            .collect(java.util.stream.Collectors.toList());
    }
}
