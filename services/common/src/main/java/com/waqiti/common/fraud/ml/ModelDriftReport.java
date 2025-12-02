package com.waqiti.common.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive model drift detection and reporting for fraud detection models.
 * Monitors model performance degradation and data distribution changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDriftReport {
    
    /**
     * Model identifier being monitored
     */
    private String modelId;
    
    /**
     * Whether drift has been detected
     */
    private boolean driftDetected;
    
    /**
     * List of specific drift indicators found
     */
    private List<String> driftIndicators;
    
    /**
     * Current model performance metrics
     */
    private ModelPerformanceMetrics currentMetrics;
    
    /**
     * Baseline metrics for comparison
     */
    private ModelPerformanceMetrics baselineMetrics;
    
    /**
     * Timestamp when drift check was performed
     */
    @Builder.Default
    private LocalDateTime checkTime = LocalDateTime.now();
    
    /**
     * Overall drift severity score (0.0 to 1.0)
     */
    @Builder.Default
    private double driftSeverity = 0.0;
    
    /**
     * Data distribution drift metrics
     */
    private DataDriftMetrics dataDrift;
    
    /**
     * Performance drift metrics
     */
    private PerformanceDriftMetrics performanceDrift;
    
    /**
     * Prediction distribution drift
     */
    private PredictionDriftMetrics predictionDrift;
    
    /**
     * Feature importance drift
     */
    private Map<String, Double> featureImportanceDrift;
    
    /**
     * Recommended actions to address drift
     */
    private List<String> recommendedActions;
    
    /**
     * Drift monitoring configuration used
     */
    private DriftMonitoringConfig monitoringConfig;
    
    /**
     * Historical drift trend
     */
    private List<DriftDataPoint> driftHistory;
    
    /**
     * Time since last retraining
     */
    private long daysSinceRetraining;
    
    /**
     * Number of samples analyzed
     */
    private long samplesAnalyzed;
    
    /**
     * Statistical significance of drift detection
     */
    private double pValue;
    
    /**
     * Drift detection method used
     */
    private String detectionMethod;
    
    /**
     * Get drift severity level
     */
    public DriftSeverityLevel getSeverityLevel() {
        if (driftSeverity >= 0.8) {
            return DriftSeverityLevel.CRITICAL;
        } else if (driftSeverity >= 0.6) {
            return DriftSeverityLevel.HIGH;
        } else if (driftSeverity >= 0.4) {
            return DriftSeverityLevel.MEDIUM;
        } else if (driftSeverity >= 0.2) {
            return DriftSeverityLevel.LOW;
        } else {
            return DriftSeverityLevel.MINIMAL;
        }
    }
    
    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return driftDetected && 
               (getSeverityLevel() == DriftSeverityLevel.CRITICAL ||
                getSeverityLevel() == DriftSeverityLevel.HIGH);
    }
    
    /**
     * Check if model retraining is recommended
     */
    public boolean recommendsRetraining() {
        return driftDetected && 
               (driftSeverity >= 0.5 || daysSinceRetraining >= 30);
    }
    
    /**
     * Get priority level for addressing this drift
     */
    public Priority getPriority() {
        if (getSeverityLevel() == DriftSeverityLevel.CRITICAL) {
            return Priority.URGENT;
        } else if (getSeverityLevel() == DriftSeverityLevel.HIGH) {
            return Priority.HIGH;
        } else if (getSeverityLevel() == DriftSeverityLevel.MEDIUM) {
            return Priority.MEDIUM;
        } else {
            return Priority.LOW;
        }
    }
    
    /**
     * Create detailed drift summary
     */
    public String getDriftSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Model: %s, ", modelId));
        summary.append(String.format("Drift Detected: %s, ", driftDetected));
        summary.append(String.format("Severity: %.3f (%s), ", driftSeverity, getSeverityLevel()));
        summary.append(String.format("Priority: %s, ", getPriority()));
        summary.append(String.format("Samples: %d, ", samplesAnalyzed));
        summary.append(String.format("Days Since Retrain: %d", daysSinceRetraining));
        
        if (driftIndicators != null && !driftIndicators.isEmpty()) {
            summary.append(String.format(", Indicators: [%s]", String.join(", ", driftIndicators)));
        }
        
        return summary.toString();
    }
    
    /**
     * Get performance degradation percentage
     */
    public double getPerformanceDegradation() {
        if (currentMetrics == null || baselineMetrics == null) {
            return 0.0;
        }
        
        double currentAccuracy = currentMetrics.getAccuracy();
        double baselineAccuracy = baselineMetrics.getAccuracy();
        
        if (baselineAccuracy == 0.0) {
            return 0.0;
        }
        
        return ((baselineAccuracy - currentAccuracy) / baselineAccuracy) * 100.0;
    }
    
    /**
     * Create alert message for monitoring systems
     */
    public String createAlertMessage() {
        if (!driftDetected) {
            return null;
        }
        
        StringBuilder alert = new StringBuilder();
        alert.append(String.format("MODEL DRIFT ALERT - %s\n", getSeverityLevel()));
        alert.append(String.format("Model: %s\n", modelId));
        alert.append(String.format("Drift Severity: %.1f%%\n", driftSeverity * 100));
        alert.append(String.format("Performance Degradation: %.1f%%\n", getPerformanceDegradation()));
        
        if (driftIndicators != null && !driftIndicators.isEmpty()) {
            alert.append(String.format("Key Issues: %s\n", String.join("; ", driftIndicators)));
        }
        
        if (recommendedActions != null && !recommendedActions.isEmpty()) {
            alert.append(String.format("Recommended Actions: %s\n", String.join("; ", recommendedActions)));
        }
        
        alert.append(String.format("Detection Time: %s", checkTime));
        
        return alert.toString();
    }
    
    /**
     * Check if this report indicates healthy model
     */
    public boolean isModelHealthy() {
        return !driftDetected || getSeverityLevel() == DriftSeverityLevel.MINIMAL;
    }
    
    /**
     * Data drift metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataDriftMetrics {
        private double distributionShift;
        private Map<String, Double> featureDrift;
        private double overallDriftScore;
        private String detectionMethod;
    }
    
    /**
     * Performance drift metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceDriftMetrics {
        private double accuracyDrift;
        private double precisionDrift;
        private double recallDrift;
        private double f1Drift;
        private double latencyDrift;
    }
    
    /**
     * Prediction drift metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionDriftMetrics {
        private double predictionShift;
        private double confidenceShift;
        private Map<String, Double> classDistributionShift;
        private double thresholdOptimality;
    }
    
    /**
     * Drift monitoring configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriftMonitoringConfig {
        private double driftThreshold;
        private int monitoringWindowDays;
        private String detectionMethod;
        private double significanceLevel;
    }
    
    /**
     * Historical drift data point
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriftDataPoint {
        private LocalDateTime timestamp;
        private double driftScore;
        private String driftType;
        private boolean actionTaken;
    }
    
    /**
     * Drift severity levels
     */
    public enum DriftSeverityLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Priority levels for drift resolution
     */
    public enum Priority {
        LOW, MEDIUM, HIGH, URGENT
    }
}