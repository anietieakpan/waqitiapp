package com.waqiti.common.fraud.scoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Machine Learning model prediction result for fraud detection
 * 
 * Contains comprehensive ML model outputs including predictions,
 * feature importance, model metadata, and confidence metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLModelResult {

    /**
     * Fraud probability score (0.0 to 1.0)
     */
    private double fraudProbability;

    /**
     * Model confidence in the prediction (0.0 to 1.0)
     */
    private double confidence;

    /**
     * Binary classification result
     */
    private boolean isFraud;

    /**
     * Model identifier and version
     */
    private ModelInfo modelInfo;

    /**
     * Feature importance scores
     */
    private Map<String, Double> featureImportance;

    /**
     * Raw model outputs and scores
     */
    private Map<String, Double> rawScores;

    /**
     * Prediction timestamp
     */
    private LocalDateTime predictedAt;

    /**
     * Model performance metrics
     */
    private ModelPerformanceMetrics performanceMetrics;

    /**
     * Feature values used for prediction
     */
    private Map<String, Object> featureValues;

    /**
     * Anomaly scores for individual features
     */
    private Map<String, Double> featureAnomalyScores;

    /**
     * Model explanation and interpretability
     */
    private ModelExplanation explanation;

    /**
     * Warning flags and model quality indicators
     */
    private List<String> modelWarnings;

    /**
     * Ensemble model results (if applicable)
     */
    private List<EnsembleModelResult> ensembleResults;

    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo {
        private String modelId;
        private String modelName;
        private String modelVersion;
        private String algorithmType;
        private LocalDateTime trainedAt;
        private LocalDateTime lastUpdated;
        private String deploymentEnvironment;
        private Map<String, Object> hyperparameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformanceMetrics {
        private double accuracy;
        private double precision;
        private double recall;
        private double f1Score;
        private double auc;
        private double falsePositiveRate;
        private double falseNegativeRate;
        private LocalDateTime metricsCalculatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelExplanation {
        private String explanationMethod;
        private List<FeatureExplanation> featureExplanations;
        private String decisionPath;
        private String humanReadableExplanation;
        private Map<String, Object> additionalExplanations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureExplanation {
        private String featureName;
        private Object featureValue;
        private double impact;
        private String impactDirection; // "increases_fraud_risk" or "decreases_fraud_risk"
        private String description;
        private double shapValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnsembleModelResult {
        private String modelName;
        private double fraudProbability;
        private double weight;
        private double confidence;
        private Map<String, Double> individualFeatureScores;
    }

    // Enums
    public enum PredictionQuality {
        HIGH("High confidence prediction"),
        MEDIUM("Medium confidence prediction"),
        LOW("Low confidence prediction"),
        UNCERTAIN("Uncertain prediction");

        private final String description;

        PredictionQuality(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public enum FraudRiskCategory {
        MINIMAL(0.0, 0.1, "Very low fraud risk"),
        LOW(0.1, 0.3, "Low fraud risk"),
        MODERATE(0.3, 0.6, "Moderate fraud risk"),
        HIGH(0.6, 0.8, "High fraud risk"),
        CRITICAL(0.8, 1.0, "Critical fraud risk");

        private final double minThreshold;
        private final double maxThreshold;
        private final String description;

        FraudRiskCategory(double minThreshold, double maxThreshold, String description) {
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
            this.description = description;
        }

        public double getMinThreshold() { return minThreshold; }
        public double getMaxThreshold() { return maxThreshold; }
        public String getDescription() { return description; }

        public static FraudRiskCategory fromProbability(double probability) {
            for (FraudRiskCategory category : values()) {
                if (probability >= category.minThreshold && probability < category.maxThreshold) {
                    return category;
                }
            }
            return CRITICAL;
        }
    }

    // Utility methods
    public FraudRiskCategory getRiskCategory() {
        return FraudRiskCategory.fromProbability(fraudProbability);
    }

    public PredictionQuality getPredictionQuality() {
        if (confidence >= 0.9) return PredictionQuality.HIGH;
        if (confidence >= 0.7) return PredictionQuality.MEDIUM;
        if (confidence >= 0.5) return PredictionQuality.LOW;
        return PredictionQuality.UNCERTAIN;
    }

    public boolean isHighConfidencePrediction() {
        return confidence >= 0.8;
    }

    public boolean requiresManualReview() {
        return getPredictionQuality() == PredictionQuality.UNCERTAIN || 
               (fraudProbability >= 0.5 && confidence < 0.7);
    }

    public List<String> getTopInfluentialFeatures(int limit) {
        if (featureImportance == null) return List.of();

        return featureImportance.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Get top 5 most important features that influenced the fraud prediction
     * This is used by the scoring engine to provide explainability
     */
    public List<String> getTopFeatures() {
        return getTopInfluentialFeatures(5);
    }

    public String getSummary() {
        return String.format("ML Fraud Probability: %.2f (%s), Confidence: %.2f (%s), Model: %s", 
                           fraudProbability, 
                           getRiskCategory().name(),
                           confidence, 
                           getPredictionQuality().name(),
                           modelInfo != null ? modelInfo.getModelName() : "Unknown");
    }

    public boolean hasModelWarnings() {
        return modelWarnings != null && !modelWarnings.isEmpty();
    }

    public double getWeightedFraudScore() {
        return fraudProbability * confidence;
    }

    public Map<String, String> getInterpretableResults() {
        Map<String, String> results = new java.util.HashMap<>();
        
        results.put("fraud_probability", String.format("%.2f%%", fraudProbability * 100));
        results.put("risk_category", getRiskCategory().getDescription());
        results.put("prediction_quality", getPredictionQuality().getDescription());
        results.put("confidence", String.format("%.2f%%", confidence * 100));
        
        if (modelInfo != null) {
            results.put("model_name", modelInfo.getModelName());
            results.put("model_version", modelInfo.getModelVersion());
        }
        
        return results;
    }

    public boolean isModelDriftDetected() {
        return modelWarnings != null && 
               modelWarnings.stream().anyMatch(warning -> 
                   warning.toLowerCase().contains("drift") || 
                   warning.toLowerCase().contains("degradation"));
    }

    public LocalDateTime getModelAge() {
        if (modelInfo != null && modelInfo.getTrainedAt() != null) {
            return modelInfo.getTrainedAt();
        }
        return null;
    }
}