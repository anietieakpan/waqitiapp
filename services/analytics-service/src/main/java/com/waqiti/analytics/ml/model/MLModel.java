package com.waqiti.analytics.ml.model;

/**
 * Common interface for all ML model types
 */
public interface MLModel {
    
    /**
     * Make prediction for multiple outputs
     * @param features Input feature vector
     * @return Array of predictions
     */
    double[] predict(double[] features);
    
    /**
     * Make single prediction (for regression or binary classification)
     * @param features Input feature vector
     * @return Single prediction value
     */
    double predict(double[] features);
    
    /**
     * Get model version
     * @return Model version string
     */
    String getModelVersion();
    
    /**
     * Get model type
     * @return Model type string
     */
    String getModelType();
}