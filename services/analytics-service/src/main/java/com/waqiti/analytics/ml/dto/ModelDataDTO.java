package com.waqiti.analytics.ml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Data Transfer Object for ML Model Data.
 *
 * SECURITY: This class is used for secure JSON-based serialization/deserialization
 * of ML model data, replacing the insecure ObjectInputStream pattern.
 *
 * JSON serialization prevents Remote Code Execution (RCE) attacks that are possible
 * with Java's native object serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Model coefficients/weights
     */
    @JsonProperty("coefficients")
    private double[] coefficients;

    /**
     * Model intercept/bias term
     */
    @JsonProperty("intercept")
    private Double intercept;

    /**
     * Cluster centers for clustering models
     */
    @JsonProperty("cluster_centers")
    private double[][] clusterCenters;

    /**
     * Feature importance scores
     */
    @JsonProperty("feature_importance")
    private double[] featureImportance;

    /**
     * Model hyperparameters
     */
    @JsonProperty("hyperparameters")
    private java.util.Map<String, Object> hyperparameters;

    /**
     * Model metadata
     */
    @JsonProperty("metadata")
    private ModelMetadata metadata;

    /**
     * Training statistics
     */
    @JsonProperty("training_stats")
    private TrainingStatistics trainingStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelMetadata implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("model_type")
        private String modelType;

        @JsonProperty("algorithm")
        private String algorithm;

        @JsonProperty("version")
        private String version;

        @JsonProperty("trained_at")
        private Long trainedAt;

        @JsonProperty("training_duration_ms")
        private Long trainingDurationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrainingStatistics implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("accuracy")
        private Double accuracy;

        @JsonProperty("precision")
        private Double precision;

        @JsonProperty("recall")
        private Double recall;

        @JsonProperty("f1_score")
        private Double f1Score;

        @JsonProperty("r_squared")
        private Double rSquared;

        @JsonProperty("mean_squared_error")
        private Double meanSquaredError;

        @JsonProperty("training_samples")
        private Integer trainingSamples;

        @JsonProperty("validation_samples")
        private Integer validationSamples;
    }
}
