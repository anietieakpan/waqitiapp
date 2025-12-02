package com.waqiti.frauddetection.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Scikit-Learn model prediction response
 * Production-ready DTO for Scikit-Learn serving responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScikitLearnPredictionResponse {

    /**
     * Prediction identifier
     */
    private String predictionId;

    /**
     * Model name/version used for prediction
     */
    private String modelName;

    private String modelVersion;

    /**
     * Algorithm used (RandomForest, GradientBoosting, etc.)
     */
    private String algorithm;

    /**
     * Predicted class/label
     */
    private String predictedClass;

    /**
     * Predicted value (for regression models)
     */
    private Double predictedValue;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidenceScore;

    /**
     * Fraud probability (0.0 - 1.0)
     */
    private Double fraudProbability;

    /**
     * Class probabilities for all classes
     */
    private Map<String, Double> classProbabilities;

    /**
     * Decision function scores
     */
    private List<Double> decisionScores;

    /**
     * Feature importance scores (for tree-based models)
     */
    private Map<String, Double> featureImportance;

    /**
     * SHAP values for explainability
     */
    private Map<String, Double> shapValues;

    /**
     * Model inference latency in milliseconds
     */
    private Long inferenceLatencyMs;

    /**
     * Timestamp of prediction
     */
    @Builder.Default
    private LocalDateTime predictedAt = LocalDateTime.now();

    /**
     * Additional model outputs/metadata
     */
    private Map<String, Object> metadata;

    /**
     * Explanation/reasoning for the prediction
     */
    private String explanation;

    /**
     * Top N predictions (for multi-class scenarios)
     */
    private List<PredictionCandidate> topPredictions;

    /**
     * Model performance metrics at prediction time
     */
    private ModelPerformanceSnapshot performanceSnapshot;

    /**
     * Whether prediction is considered reliable
     */
    @Builder.Default
    private Boolean isReliable = true;

    /**
     * Warnings or anomalies detected during prediction
     */
    private List<String> warnings;

    /**
     * Calibration score (how well-calibrated the probabilities are)
     */
    private Double calibrationScore;

    /**
     * Individual prediction candidate
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionCandidate {
        private String label;
        private Double probability;
        private Double score;
        private Integer rank;
    }

    /**
     * Model performance snapshot at prediction time
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformanceSnapshot {
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Double auc;
    }

    /**
     * Get the primary fraud indicator (true if fraud probability > 0.5)
     */
    public boolean isFraudulent() {
        return fraudProbability != null && fraudProbability > 0.5;
    }

    /**
     * Get risk level based on fraud probability
     */
    public String getRiskLevel() {
        if (fraudProbability == null) return "UNKNOWN";
        if (fraudProbability >= 0.9) return "CRITICAL";
        if (fraudProbability >= 0.7) return "HIGH";
        if (fraudProbability >= 0.5) return "MEDIUM";
        if (fraudProbability >= 0.3) return "LOW";
        return "MINIMAL";
    }

    /**
     * Check if prediction confidence is acceptable (>= 0.7)
     */
    public boolean isConfidentPrediction() {
        return confidenceScore != null && confidenceScore >= 0.7;
    }

    /**
     * Check if prediction is well-calibrated
     */
    public boolean isWellCalibrated() {
        return calibrationScore != null && calibrationScore >= 0.8;
    }
}
