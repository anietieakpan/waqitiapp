package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Machine Learning model risk score
 * Result from ML-based risk prediction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLRiskScore {

    private String modelId;
    private String modelVersion;
    private String modelType; // RANDOM_FOREST, GRADIENT_BOOSTING, NEURAL_NETWORK, ENSEMBLE

    private Double score; // 0.0 (low risk) to 1.0 (high risk)
    private Double probability; // Probability of fraud/risk
    private String prediction; // FRAUD, NOT_FRAUD, HIGH_RISK, LOW_RISK

    // Classification thresholds
    private Double threshold;
    private Boolean aboveThreshold;

    // Confidence metrics
    private Double confidence; // Model confidence in prediction
    private String confidenceLevel; // LOW, MEDIUM, HIGH

    // Feature importance
    private Map<String, Double> featureImportance; // feature -> importance score
    private List<String> topFeatures; // Top contributing features

    // Model performance metadata
    private Double modelAccuracy;
    private Double modelPrecision;
    private Double modelRecall;
    private Double modelF1Score;

    // Prediction details
    private Map<String, Double> classProb

abilities; // class -> probability
    private List<String> anomaliesDetected;

    // Timing
    private Instant predictedAt;
    private Long inferenceTimeMs;

    // Explainability
    private String explanation; // Human-readable explanation
    private Map<String, Object> shapValues; // SHAP values for explainability

    private Map<String, Object> metadata;
}
