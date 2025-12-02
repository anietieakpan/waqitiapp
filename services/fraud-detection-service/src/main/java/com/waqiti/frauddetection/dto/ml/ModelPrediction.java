package com.waqiti.frauddetection.dto.ml;

import com.waqiti.frauddetection.dto.RiskLevel;
import lombok.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model Prediction DTO
 *
 * Represents the output from an ML fraud detection model.
 * Contains prediction scores, class probabilities, feature importance,
 * and model metadata for explainability and auditing.
 *
 * PRODUCTION-GRADE DTO
 * - Complete prediction metadata
 * - Explainability support (SHAP values, feature importance)
 * - Model versioning and tracking
 * - Confidence intervals
 * - Multiple model ensemble support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPrediction {

    /**
     * Unique prediction ID
     */
    @NotNull
    private String predictionId;

    /**
     * Transaction ID being predicted
     */
    @NotNull
    private String transactionId;

    /**
     * Model Information
     */
    @NotNull
    private String modelName;

    @NotNull
    private String modelVersion;

    private String modelType; // e.g., "XGBoost", "TensorFlow", "PyTorch"

    /**
     * Prediction Scores
     */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double fraudProbability; // Main fraud probability (0.0 - 1.0)

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double legitimateProbability; // Probability of legitimate transaction

    /**
     * Predicted class (binary classification)
     */
    @NotNull
    private Boolean isFraud;

    /**
     * Risk Level derived from fraud probability
     */
    @NotNull
    private RiskLevel riskLevel;

    /**
     * Model confidence in prediction (0.0 - 1.0)
     * Higher = more confident
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confidence;

    /**
     * Class Probabilities (for multi-class models)
     * Class name -> probability mapping
     */
    @Builder.Default
    private Map<String, Double> classProbabilities = new HashMap<>();

    /**
     * Feature Importance (for explainability)
     * Feature name -> importance score
     */
    @Builder.Default
    private Map<String, Double> featureImportance = new HashMap<>();

    /**
     * SHAP values (SHapley Additive exPlanations)
     * For model explainability
     */
    @Builder.Default
    private Map<String, Double> shapValues = new HashMap<>();

    /**
     * Top contributing features (ranked by importance)
     */
    @Builder.Default
    private List<FeatureContribution> topContributingFeatures = new ArrayList<>();

    /**
     * Prediction Metadata
     */
    private LocalDateTime predictedAt;

    private Long predictionTimeMs; // Inference latency

    /**
     * Ensemble model information (if using multiple models)
     */
    @Builder.Default
    private List<EnsemblePrediction> ensemblePredictions = new ArrayList<>();

    private String ensembleMethod; // e.g., "voting", "averaging", "stacking"

    /**
     * Confidence Intervals (for uncertainty quantification)
     */
    private Double confidenceLowerBound;
    private Double confidenceUpperBound;

    /**
     * Model calibration score (0.0 - 1.0)
     * How well the predicted probabilities match actual frequencies
     */
    private Double calibrationScore;

    /**
     * Anomaly scores (if using anomaly detection models)
     */
    private Double anomalyScore;

    /**
     * Additional metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Get top N contributing features
     */
    public List<FeatureContribution> getTopFeatures(int n) {
        return topContributingFeatures.stream()
            .limit(n)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Check if prediction is high confidence
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }

    /**
     * Check if prediction requires review (low confidence)
     */
    public boolean requiresReview() {
        return confidence != null && confidence < 0.6;
    }

    /**
     * Get fraud probability as percentage
     */
    public double getFraudProbabilityPercentage() {
        return fraudProbability * 100.0;
    }

    /**
     * Get risk score (0-100 scale)
     */
    public double getRiskScore() {
        return fraudProbability * 100.0;
    }

    /**
     * Check if ensemble prediction (multiple models)
     */
    public boolean isEnsemble() {
        return ensemblePredictions != null && !ensemblePredictions.isEmpty();
    }

    /**
     * Get ensemble agreement rate
     * Returns percentage of models that agree with final prediction
     */
    public double getEnsembleAgreement() {
        if (!isEnsemble()) {
            return 1.0; // Single model = 100% agreement
        }

        long agreeing = ensemblePredictions.stream()
            .filter(p -> p.getIsFraud().equals(this.isFraud))
            .count();

        return (double) agreeing / ensemblePredictions.size();
    }

    /**
     * Feature Contribution Inner Class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureContribution {
        private String featureName;
        private String featureDisplayName;
        private Double featureValue;
        private Double importance;
        private Double shapValue;
        private String impact; // "positive" or "negative"

        public boolean isPositiveImpact() {
            return "positive".equalsIgnoreCase(impact);
        }
    }

    /**
     * Ensemble Prediction Inner Class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnsemblePrediction {
        private String modelName;
        private String modelVersion;
        private Double fraudProbability;
        private Boolean isFraud;
        private Double weight; // Model weight in ensemble
        private Long predictionTimeMs;
    }

    /**
     * Create summary for logging/monitoring
     */
    public String getSummary() {
        return String.format(
            "Prediction[id=%s, model=%s v%s, fraud=%.2f%%, risk=%s, confidence=%.2f, latency=%dms]",
            predictionId,
            modelName,
            modelVersion,
            getFraudProbabilityPercentage(),
            riskLevel,
            confidence,
            predictionTimeMs
        );
    }
}
