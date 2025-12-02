package com.waqiti.frauddetection.ml;

import java.util.Map;

/**
 * Fraud ML Model Interface
 *
 * Common interface for all fraud detection ML models.
 * Supports multiple model types: XGBoost, RandomForest, TensorFlow, Rule-based.
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
public interface IFraudMLModel {

    /**
     * Predict fraud probability
     *
     * @param features Feature map with engineered features
     * @return Fraud probability (0.0 - 1.0)
     * @throws Exception if prediction fails
     */
    double predict(Map<String, Object> features) throws Exception;

    /**
     * Check if model is loaded and ready for predictions
     *
     * @return true if model is ready, false otherwise
     */
    boolean isReady();

    /**
     * Get model name for logging and monitoring
     *
     * @return Model name (e.g., "XGBoost", "RandomForest", "TensorFlow")
     */
    String getModelName();
}
