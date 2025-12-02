package com.waqiti.frauddetection.integration.tensorflow.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TensorFlow Prediction Response DTO
 *
 * Response from TensorFlow Serving API containing model predictions,
 * probabilities, and metadata.
 *
 * PRODUCTION-GRADE DTO
 * - Complete TF Serving response mapping
 * - Multiple output tensor support
 * - Performance metrics
 * - Error handling
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TensorFlowPredictionResponse {

    /**
     * Model information
     */
    private String modelName;
    private String modelVersion;
    private Long modelVersionNumber;

    /**
     * Prediction outputs
     */
    @Builder.Default
    private List<Double> predictions = new ArrayList<>();

    /**
     * Class probabilities (for classification)
     */
    @Builder.Default
    private List<Double> probabilities = new ArrayList<>();

    /**
     * Multiple output support (output name -> values)
     */
    @Builder.Default
    private Map<String, List<Double>> outputs = new HashMap<>();

    /**
     * TensorFlow Serving metadata
     */
    private String signature; // e.g., "serving_default"

    @Builder.Default
    private List<String> outputKeys = new ArrayList<>();

    /**
     * Prediction metadata
     */
    private LocalDateTime predictedAt;
    private Long predictionTimeMs;
    private Long modelLoadTimeMs;

    /**
     * Error information
     */
    private Boolean success;
    private String errorMessage;
    private String errorCode;

    /**
     * Additional metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Get primary prediction (first output)
     */
    public Double getPrimaryPrediction() {
        return predictions != null && !predictions.isEmpty() ? predictions.get(0) : null;
    }

    /**
     * Get fraud probability (assuming binary classification)
     */
    public Double getFraudProbability() {
        if (probabilities != null && probabilities.size() >= 2) {
            return probabilities.get(1); // Index 1 = fraud class
        }
        return getPrimaryPrediction();
    }

    /**
     * Get legitimate probability
     */
    public Double getLegitimProbability() {
        if (probabilities != null && !probabilities.isEmpty()) {
            return probabilities.get(0); // Index 0 = legitimate class
        }
        return null;
    }

    /**
     * Check if prediction successful
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * Check if prediction failed
     */
    public boolean isFailed() {
        return !isSuccessful();
    }

    /**
     * Get output by name
     */
    public List<Double> getOutput(String outputName) {
        return outputs.get(outputName);
    }

    /**
     * Check if has specific output
     */
    public boolean hasOutput(String outputName) {
        return outputs.containsKey(outputName);
    }
}
