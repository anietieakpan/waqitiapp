package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Training Job Configuration DTO
 *
 * Configuration for ML model training jobs.
 * Includes hyperparameters, training settings, and resource allocation.
 *
 * PRODUCTION-GRADE DTO
 * - Comprehensive hyperparameter configuration
 * - Resource allocation settings
 * - Early stopping criteria
 * - Distributed training support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingJobConfig {

    @NotNull
    private String jobId;

    @NotNull
    private String modelName;

    private String modelType; // "xgboost", "tensorflow", "pytorch"

    /**
     * Dataset configuration
     */
    @NotNull
    private String datasetId;

    private Double trainTestSplit;
    private Double validationSplit;
    private Integer randomSeed;

    /**
     * Hyperparameters
     */
    @Builder.Default
    private Map<String, Object> hyperparameters = new HashMap<>();

    /**
     * Training parameters
     */
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
    private String optimizer; // "adam", "sgd", "rmsprop"
    private String lossFunction;

    /**
     * Early stopping
     */
    private Boolean earlyStoppingEnabled;
    private Integer patience; // epochs
    private String monitorMetric; // "val_loss", "val_accuracy"

    /**
     * Resource allocation
     */
    private Integer cpuCores;
    private Integer gpuCount;
    private String gpuType;
    private Integer memoryGb;

    /**
     * Distributed training
     */
    private Boolean distributedTraining;
    private Integer workerNodes;
    private String distributionStrategy; // "mirrored", "parameter_server"

    /**
     * Output configuration
     */
    private String outputPath;
    private Boolean saveCheckpoints;
    private Integer checkpointFrequency; // epochs

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
