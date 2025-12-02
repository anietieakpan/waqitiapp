package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Generic ML model prediction result
 * Used for various ML-based risk assessments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPrediction {

    private String predictionId;
    private String modelId;
    private String modelName;
    private String modelVersion;
    private String modelType; // CLASSIFICATION, REGRESSION, CLUSTERING, ANOMALY_DETECTION

    // Prediction result
    private String predictedClass; // For classification
    private Double predictedValue; // For regression
    private Integer predictedCluster; // For clustering
    private Boolean isAnomalous; // For anomaly detection

    // Probabilities (for classification)
    private Map<String, Double> classProbabilities;
    private Double confidence; // Overall confidence

    // Feature importance
    private Map<String, Double> featureImportance;
    private List<String> topFeatures; // Most influential features

    // Model performance (if available)
    private Double modelAccuracy;
    private Double modelPrecision;
    private Double modelRecall;
    private Double modelF1Score;
    private Double modelAuc; // Area Under Curve

    // Prediction metadata
    private Instant predictedAt;
    private Long inferenceTimeMs;
    private String predictionStatus; // SUCCESS, FAILED, TIMEOUT
    private String errorMessage;

    // Explainability
    private String explanation; // Human-readable explanation
    private Map<String, Object> shapValues; // SHAP values
    private Map<String, Object> limeExplanation; // LIME explanation

    // Input features
    private FeatureVector inputFeatures;
    private Integer featureCount;

    // Thresholds
    private Double decisionThreshold;
    private Boolean aboveThreshold;

    private Map<String, Object> metadata;
}
