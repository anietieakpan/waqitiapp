package com.waqiti.common.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive performance report for ML models in fraud detection system.
 * Provides detailed analytics and insights for model monitoring and optimization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPerformanceReport {
    
    /**
     * Model identifier
     */
    private String modelId;
    
    /**
     * Model type (e.g., RANDOM_FOREST, NEURAL_NETWORK)
     */
    private MachineLearningModelService.ModelType modelType;
    
    /**
     * Model version
     */
    private String version;
    
    /**
     * Primary accuracy metric (0.0 to 1.0)
     */
    private double accuracy;
    
    /**
     * Precision score (0.0 to 1.0)
     */
    private double precision;
    
    /**
     * Recall score (0.0 to 1.0)
     */
    private double recall;
    
    /**
     * F1 score (0.0 to 1.0)
     */
    private double f1Score;
    
    /**
     * Area under ROC curve (0.0 to 1.0)
     */
    private double auc;
    
    /**
     * Total predictions made
     */
    private long totalPredictions;
    
    /**
     * Average prediction latency in milliseconds
     */
    private double averageLatency;
    
    /**
     * Last report update time
     */
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    /**
     * Performance trend over time
     */
    private List<PerformanceTrendPoint> performanceTrend;
    
    /**
     * Feature importance rankings
     */
    private Map<String, Double> featureImportance;
    
    /**
     * Confusion matrix breakdown
     */
    private ConfusionMatrixStats confusionMatrix;
    
    /**
     * Latency distribution statistics
     */
    private LatencyStats latencyStats;
    
    /**
     * Business impact metrics
     */
    private BusinessImpactMetrics businessImpact;
    
    /**
     * Error analysis breakdown
     */
    private ErrorAnalysis errorAnalysis;
    
    /**
     * Resource utilization metrics
     */
    private ResourceUtilizationStats resourceStats;
    
    /**
     * Model comparison with benchmarks
     */
    private ComparisonMetrics comparisonMetrics;
    
    /**
     * Recommendations for improvement
     */
    private List<String> recommendations;
    
    /**
     * Model health status
     */
    private ModelHealthStatus healthStatus;
    
    /**
     * A/B testing results if applicable
     */
    private ABTestingResults abTestResults;
    
    /**
     * Data quality impact on performance
     */
    private DataQualityImpact dataQualityImpact;
    
    /**
     * Get overall performance grade
     */
    public PerformanceGrade getPerformanceGrade() {
        double overallScore = calculateOverallScore();
        
        if (overallScore >= 0.9) {
            return PerformanceGrade.EXCELLENT;
        } else if (overallScore >= 0.8) {
            return PerformanceGrade.GOOD;
        } else if (overallScore >= 0.7) {
            return PerformanceGrade.ACCEPTABLE;
        } else if (overallScore >= 0.6) {
            return PerformanceGrade.POOR;
        } else {
            return PerformanceGrade.UNACCEPTABLE;
        }
    }
    
    /**
     * Calculate weighted overall performance score
     */
    private double calculateOverallScore() {
        // Weighted combination of key metrics
        double accuracyWeight = 0.3;
        double precisionWeight = 0.25;
        double recallWeight = 0.25;
        double latencyWeight = 0.1;
        double reliabilityWeight = 0.1;
        
        double latencyScore = Math.max(0.0, 1.0 - (averageLatency / 1000.0)); // Normalize latency
        double reliabilityScore = Math.max(0.0, 1.0 - getErrorRate());
        
        return (accuracy * accuracyWeight) +
               (precision * precisionWeight) +
               (recall * recallWeight) +
               (latencyScore * latencyWeight) +
               (reliabilityScore * reliabilityWeight);
    }
    
    /**
     * Get error rate from total predictions
     */
    private double getErrorRate() {
        if (errorAnalysis != null && totalPredictions > 0) {
            return (double) errorAnalysis.getTotalErrors() / totalPredictions;
        }
        return 0.0;
    }
    
    /**
     * Check if model meets production standards
     */
    public boolean meetsProductionStandards() {
        return accuracy >= 0.85 &&
               precision >= 0.8 &&
               recall >= 0.8 &&
               averageLatency <= 200.0 &&
               getErrorRate() <= 0.05;
    }
    
    /**
     * Get key performance indicators summary
     */
    public String getKPISummary() {
        return String.format(
            "Accuracy: %.1f%%, Precision: %.1f%%, Recall: %.1f%%, " +
            "F1: %.1f%%, AUC: %.1f%%, Latency: %.0fms, " +
            "Predictions: %,d, Grade: %s",
            accuracy * 100, precision * 100, recall * 100,
            f1Score * 100, auc * 100, averageLatency,
            totalPredictions, getPerformanceGrade()
        );
    }
    
    /**
     * Get detailed performance analysis
     */
    public String getDetailedAnalysis() {
        StringBuilder analysis = new StringBuilder();
        
        analysis.append("=== MODEL PERFORMANCE REPORT ===\n");
        analysis.append(String.format("Model: %s (%s) v%s\n", modelId, modelType, version));
        analysis.append(String.format("Report Date: %s\n\n", lastUpdated));
        
        analysis.append("CORE METRICS:\n");
        analysis.append(String.format("- Accuracy: %.3f (%.1f%%)\n", accuracy, accuracy * 100));
        analysis.append(String.format("- Precision: %.3f (%.1f%%)\n", precision, precision * 100));
        analysis.append(String.format("- Recall: %.3f (%.1f%%)\n", recall, recall * 100));
        analysis.append(String.format("- F1 Score: %.3f (%.1f%%)\n", f1Score, f1Score * 100));
        analysis.append(String.format("- AUC-ROC: %.3f (%.1f%%)\n\n", auc, auc * 100));
        
        analysis.append("OPERATIONAL METRICS:\n");
        analysis.append(String.format("- Total Predictions: %,d\n", totalPredictions));
        analysis.append(String.format("- Average Latency: %.1f ms\n", averageLatency));
        analysis.append(String.format("- Error Rate: %.2f%%\n\n", getErrorRate() * 100));
        
        analysis.append(String.format("OVERALL GRADE: %s\n", getPerformanceGrade()));
        analysis.append(String.format("PRODUCTION READY: %s\n", meetsProductionStandards() ? "YES" : "NO"));
        
        if (recommendations != null && !recommendations.isEmpty()) {
            analysis.append("\nRECOMMENDATIONS:\n");
            recommendations.forEach(rec -> analysis.append("- ").append(rec).append("\n"));
        }
        
        return analysis.toString();
    }
    
    /**
     * Create performance alert if thresholds are breached
     */
    public PerformanceAlert createAlert() {
        if (meetsProductionStandards() && getPerformanceGrade().ordinal() <= PerformanceGrade.GOOD.ordinal()) {
            return null; // No alert needed
        }
        
        AlertSeverity severity = determineAlertSeverity();
        
        return PerformanceAlert.builder()
                .modelId(modelId)
                .severity(severity)
                .message(createAlertMessage())
                .metrics(this)
                .timestamp(LocalDateTime.now())
                .actionRequired(severity.ordinal() >= AlertSeverity.HIGH.ordinal())
                .build();
    }
    
    /**
     * Determine alert severity based on performance
     */
    private AlertSeverity determineAlertSeverity() {
        if (getPerformanceGrade() == PerformanceGrade.UNACCEPTABLE) {
            return AlertSeverity.CRITICAL;
        } else if (getPerformanceGrade() == PerformanceGrade.POOR) {
            return AlertSeverity.HIGH;
        } else if (!meetsProductionStandards()) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }
    
    /**
     * Create alert message for monitoring systems
     */
    private String createAlertMessage() {
        StringBuilder message = new StringBuilder();
        message.append(String.format("Model %s performance degraded to %s grade. ", modelId, getPerformanceGrade()));
        
        if (accuracy < 0.8) {
            message.append(String.format("Low accuracy: %.1f%%. ", accuracy * 100));
        }
        if (averageLatency > 200) {
            message.append(String.format("High latency: %.0fms. ", averageLatency));
        }
        if (getErrorRate() > 0.05) {
            message.append(String.format("High error rate: %.1f%%. ", getErrorRate() * 100));
        }
        
        return message.toString();
    }
    
    /**
     * Supporting classes for comprehensive metrics
     */
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceTrendPoint {
        private LocalDateTime timestamp;
        private double accuracy;
        private double latency;
        private long predictions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfusionMatrixStats {
        private long truePositives;
        private long trueNegatives;
        private long falsePositives;
        private long falseNegatives;
        
        public double getFalsePositiveRate() {
            long totalNegatives = trueNegatives + falsePositives;
            return totalNegatives > 0 ? (double) falsePositives / totalNegatives : 0.0;
        }
        
        public double getFalseNegativeRate() {
            long totalPositives = truePositives + falseNegatives;
            return totalPositives > 0 ? (double) falseNegatives / totalPositives : 0.0;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyStats {
        private double min;
        private double max;
        private double median;
        private double p95;
        private double p99;
        private double stdDev;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessImpactMetrics {
        private double fraudPrevented;
        private double falsePositivesCost;
        private double processingCostSavings;
        private double customerSatisfactionImpact;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorAnalysis {
        private long totalErrors;
        private Map<String, Long> errorsByType;
        private List<String> commonErrorPatterns;
        private String rootCauseAnalysis;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUtilizationStats {
        private double cpuUsage;
        private double memoryUsage;
        private double diskUsage;
        private double throughput;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonMetrics {
        private double performanceVsBenchmark;
        private String benchmarkModel;
        private Map<String, Double> competitorComparison;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ABTestingResults {
        private String testName;
        private double controlAccuracy;
        private double treatmentAccuracy;
        private double statisticalSignificance;
        private String recommendation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataQualityImpact {
        private double dataQualityScore;
        private Map<String, Double> qualityMetricsByFeature;
        private double estimatedPerformanceImpact;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceAlert {
        private String modelId;
        private AlertSeverity severity;
        private String message;
        private ModelPerformanceReport metrics;
        private LocalDateTime timestamp;
        private boolean actionRequired;
    }
    
    /**
     * Performance grade enumeration
     */
    public enum PerformanceGrade {
        EXCELLENT, GOOD, ACCEPTABLE, POOR, UNACCEPTABLE
    }
    
    /**
     * Model health status
     */
    public enum ModelHealthStatus {
        HEALTHY, WARNING, DEGRADED, CRITICAL, OFFLINE
    }
    
    /**
     * Alert severity levels
     */
    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}