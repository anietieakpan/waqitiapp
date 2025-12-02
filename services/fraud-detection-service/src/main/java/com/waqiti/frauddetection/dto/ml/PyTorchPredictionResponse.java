package com.waqiti.frauddetection.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PyTorch model prediction response
 * Production-ready DTO for PyTorch serving responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PyTorchPredictionResponse {

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
     * Predicted class/label
     */
    private String predictedClass;

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
     * Raw prediction scores/logits
     */
    private List<Double> rawScores;

    /**
     * Feature importance scores
     */
    private Map<String, Double> featureImportance;

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
     * Model health status at prediction time
     */
    private String modelHealthStatus;

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
}
