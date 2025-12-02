package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ML Model Performance
 * 
 * Performance metrics and statistics for a machine learning fraud detection model.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLModelPerformance {
    
    /**
     * Model ID
     */
    private String modelId;
    
    /**
     * Model name
     */
    private String modelName;
    
    /**
     * Model version
     */
    private String version;
    
    /**
     * Performance evaluation period
     */
    private EvaluationPeriod evaluationPeriod;
    
    /**
     * Core performance metrics
     */
    private CoreMetrics coreMetrics;
    
    /**
     * Confusion matrix
     */
    private ConfusionMatrix confusionMatrix;
    
    /**
     * ROC curve data
     */
    private ROCData rocData;
    
    /**
     * Precision-Recall curve data
     */
    private PrecisionRecallData precisionRecallData;
    
    /**
     * Feature importance
     */
    private List<FeatureImportance> featureImportance;
    
    /**
     * Performance by data segments
     */
    private Map<String, CoreMetrics> segmentPerformance;
    
    /**
     * Model drift metrics
     */
    private DriftMetrics driftMetrics;
    
    /**
     * Performance trends over time
     */
    private List<PerformanceTrend> trends;
    
    /**
     * Model health indicators
     */
    private ModelHealth modelHealth;
    
    /**
     * Last evaluation timestamp
     */
    private LocalDateTime lastEvaluatedAt;
    
    /**
     * Next evaluation scheduled
     */
    private LocalDateTime nextEvaluationAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationPeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String periodType; // DAILY, WEEKLY, MONTHLY
        private Long totalPredictions;
        private Long totalActuals;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreMetrics {
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Double specificity;
        private Double sensitivity;
        private Double auc;
        private Double precisionAtRecall50;
        private Double precisionAtRecall90;
        private Double falsePositiveRate;
        private Double falseNegativeRate;
        private Double matthewsCorrelationCoefficient;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfusionMatrix {
        private Long truePositives;
        private Long trueNegatives;
        private Long falsePositives;
        private Long falseNegatives;
        private Double[][] normalizedMatrix;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ROCData {
        private List<Double> falsePositiveRates;
        private List<Double> truePositiveRates;
        private List<Double> thresholds;
        private Double auc;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrecisionRecallData {
        private List<Double> precision;
        private List<Double> recall;
        private List<Double> thresholds;
        private Double averagePrecision;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureImportance {
        private String featureName;
        private Double importance;
        private String importanceType; // GAIN, SPLIT, PERMUTATION
        private Integer rank;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriftMetrics {
        private Double dataDrift;
        private Double conceptDrift;
        private Double featureDrift;
        private Double targetDrift;
        private DriftStatus driftStatus;
        private List<String> driftingFeatures;
        private LocalDateTime lastDriftCheck;
        
        public enum DriftStatus {
            NO_DRIFT,
            MINOR_DRIFT,
            MODERATE_DRIFT,
            SIGNIFICANT_DRIFT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceTrend {
        private LocalDateTime timestamp;
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Long predictionVolume;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelHealth {
        private HealthStatus overallHealth;
        private Double healthScore; // 0-100
        private List<HealthIndicator> indicators;
        private List<String> recommendations;
        
        public enum HealthStatus {
            EXCELLENT,
            GOOD,
            FAIR,
            POOR,
            CRITICAL
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class HealthIndicator {
            private String indicator;
            private String status;
            private Double value;
            private String description;
        }
    }
    
    /**
     * Check if model performance is acceptable
     */
    public boolean isPerformanceAcceptable() {
        return coreMetrics != null && 
               coreMetrics.getF1Score() != null && 
               coreMetrics.getF1Score() >= 0.8;
    }
    
    /**
     * Check if model needs retraining
     */
    public boolean needsRetraining() {
        return driftMetrics != null && 
               (driftMetrics.getDriftStatus() == DriftMetrics.DriftStatus.SIGNIFICANT_DRIFT ||
                modelHealth.getOverallHealth() == ModelHealth.HealthStatus.POOR ||
                modelHealth.getOverallHealth() == ModelHealth.HealthStatus.CRITICAL);
    }
    
    /**
     * Get model performance grade
     */
    public String getPerformanceGrade() {
        if (coreMetrics == null || coreMetrics.getF1Score() == null) {
            return "N/A";
        }
        
        double f1 = coreMetrics.getF1Score();
        if (f1 >= 0.95) return "A+";
        if (f1 >= 0.90) return "A";
        if (f1 >= 0.85) return "B+";
        if (f1 >= 0.80) return "B";
        if (f1 >= 0.75) return "C";
        return "F";
    }
}