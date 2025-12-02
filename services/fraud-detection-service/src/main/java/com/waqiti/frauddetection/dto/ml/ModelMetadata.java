package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model Metadata DTO
 *
 * Comprehensive metadata about an ML fraud detection model.
 * Includes model information, training history, performance metrics,
 * and deployment configuration.
 *
 * PRODUCTION-GRADE DTO
 * - Complete model lineage
 * - Training configuration
 * - Performance benchmarks
 * - Deployment settings
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetadata {

    @NotNull
    private String modelId;

    @NotNull
    private String modelName;

    @NotNull
    private String modelVersion;

    /**
     * Model type and framework
     */
    private String modelType; // "xgboost", "tensorflow", "pytorch", "sklearn"
    private String framework;
    private String frameworkVersion;

    /**
     * Model file information
     */
    private String modelPath;
    private String modelChecksum; // SHA-256
    private Long modelSizeBytes;

    /**
     * Training information
     */
    private LocalDateTime trainedAt;
    private String trainingDatasetId;
    private Integer trainingExamplesCount;
    private Map<String, Integer> classDistribution;

    /**
     * Feature information
     */
    @Builder.Default
    private List<String> featureNames = new ArrayList<>();

    private Integer featureCount;

    @Builder.Default
    private Map<String, String> featureTypes = new HashMap<>();

    /**
     * Hyperparameters
     */
    @Builder.Default
    private Map<String, Object> hyperparameters = new HashMap<>();

    /**
     * Performance metrics
     */
    private Double accuracy;
    private Double precision;
    private Double recall;
    private Double f1Score;
    private Double auc;
    private Double logLoss;

    /**
     * Deployment information
     */
    private String deploymentStatus; // "training", "testing", "staging", "production", "retired"
    private LocalDateTime deployedAt;
    private String deployedBy;

    /**
     * Model lineage
     */
    private String parentModelVersion;
    private String trainingJobId;

    /**
     * Additional metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public boolean isProduction() {
        return "production".equalsIgnoreCase(deploymentStatus);
    }

    public boolean isRetired() {
        return "retired".equalsIgnoreCase(deploymentStatus);
    }
}
