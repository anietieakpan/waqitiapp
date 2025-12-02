package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of machine learning model-based fraud analysis.
 * Contains predictions from multiple ML models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLAnalysisResult {

    private LocalDateTime timestamp;

    // Model predictions
    @Builder.Default
    private Map<String, Double> modelScores = new HashMap<>(); // Model name -> score

    // Ensemble results
    private Double ensembleScore; // Combined score from all models
    private String ensembleMethod; // AVERAGE, WEIGHTED, VOTING, STACKING

    // Individual model results
    private Double randomForestScore;
    private Double gradientBoostingScore;
    private Double neuralNetworkScore;
    private Double logisticRegressionScore;
    private Double xgboostScore;
    private Double isolationForestScore; // Anomaly detection

    // Model predictions
    private Boolean fraudPredicted; // Binary classification result
    private Double fraudProbability; // Probability of fraud (0.0 - 1.0)
    private Double confidenceScore; // Model confidence in prediction

    // Feature importance
    private Map<String, Double> featureImportance; // Top features contributing to decision
    private List<String> topFeatures; // Most important features

    // Model metadata
    private String modelVersion;
    private List<String> modelsUsed;
    private Integer featuresUsed;
    private String trainingDataVersion;

    // Anomaly detection
    private Boolean isAnomaly;
    private Double anomalyScore; // How anomalous the transaction is
    private List<String> anomalousFeatures;

    // Explainability
    private String explanation; // Human-readable explanation of prediction
    private Map<String, Object> shapValues; // SHAP values for explainability

    /**
     * Add model score
     */
    public void addModelScore(String modelName, Double score) {
        if (modelScores == null) {
            modelScores = new HashMap<>();
        }
        modelScores.put(modelName, score);
    }

    /**
     * Check if ML models indicate high fraud risk
     */
    public boolean isHighMLRisk() {
        return ensembleScore != null && ensembleScore > 0.7;
    }

    /**
     * Get model agreement level (how many models agree on fraud)
     */
    public double getModelAgreement() {
        if (modelScores == null || modelScores.isEmpty()) {
            return 0.0;
        }
        long fraudPredictions = modelScores.values().stream()
            .filter(score -> score > 0.5)
            .count();
        return (double) fraudPredictions / modelScores.size();
    }
}
