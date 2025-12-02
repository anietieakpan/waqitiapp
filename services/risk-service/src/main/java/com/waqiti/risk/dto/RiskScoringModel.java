package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Risk scoring model metadata and configuration
 * Represents a configured risk assessment model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoringModel {

    private String modelId;
    private String modelName;
    private String modelVersion;
    private String modelType; // RULE_BASED, ML_BASED, HYBRID, ENSEMBLE

    // Model status
    private String status; // ACTIVE, INACTIVE, TESTING, DEPRECATED
    private Boolean isDefault;
    private Integer priority; // Higher priority models are used first

    // Model configuration
    private Map<String, Double> weights; // Factor weights
    private Map<String, Double> thresholds; // Decision thresholds
    private Map<String, Object> parameters; // Model-specific parameters

    // Features
    private List<String> requiredFeatures;
    private List<String> optionalFeatures;
    private Integer totalFeatures;

    // Performance metrics
    private Double accuracy;
    private Double precision;
    private Double recall;
    private Double f1Score;
    private Double auc;
    private Double falsePositiveRate;
    private Double falseNegativeRate;

    // Validation
    private Instant lastValidated;
    private String validationStatus; // PASSED, FAILED, PENDING
    private Map<String, Object> validationMetrics;

    // Usage statistics
    private Long totalPredictions;
    private Long successfulPredictions;
    private Long failedPredictions;
    private Double averageInferenceTimeMs;

    // Versioning
    private String previousVersion;
    private Instant createdAt;
    private Instant deployedAt;
    private Instant deprecatedAt;
    private String createdBy;

    // Model artifacts
    private String modelPath; // Path to model file
    private String modelFormat; // PMML, ONNX, PICKLE, H5, TENSORFLOW
    private Long modelSizeBytes;

    // Training information
    private Instant trainedAt;
    private String trainingDataset;
    private Integer trainingS amples;
    private Map<String, Object> trainingMetrics;

    private Map<String, Object> metadata;
}
