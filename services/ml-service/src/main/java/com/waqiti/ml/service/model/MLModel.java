package com.waqiti.ml.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Common interface for all ML models in the fraud detection system
 */
public interface MLModel {
    
    /**
     * Predicts fraud probability for given features
     * 
     * @param features Feature vector for prediction
     * @return Fraud probability score between 0.0 and 1.0
     */
    double predict(double[] features);
    
    /**
     * Checks if the model is healthy and ready for inference
     */
    boolean isHealthy();
    
    /**
     * Gets model metadata including performance metrics
     */
    ModelMetadata getMetadata();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ModelMetadata {
        private String modelName;
        private String modelVersion;
        private String modelType;
        private String trainingDate;
        private double accuracy;
        private double precision;
        private double recall;
        private double f1Score;
        private int featureCount;
        private boolean isLoaded;
        private String description;
    }
}