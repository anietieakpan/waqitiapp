package com.waqiti.risk.ml;

import com.waqiti.risk.dto.FeatureVector;
import com.waqiti.risk.dto.MLPrediction;
import com.waqiti.risk.dto.TransactionRiskRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for ML-based risk prediction models
 * Supports multiple ML model implementations with async prediction
 */
public interface RiskMLModel {

    /**
     * Predict risk score for a transaction
     *
     * @param request Transaction risk request
     * @param features Engineered feature vector
     * @return ML prediction result
     */
    MLPrediction predict(TransactionRiskRequest request, FeatureVector features);

    /**
     * Async prediction for non-blocking risk assessment
     *
     * @param request Transaction risk request
     * @param features Engineered feature vector
     * @return CompletableFuture of ML prediction
     */
    CompletableFuture<MLPrediction> predictAsync(TransactionRiskRequest request, FeatureVector features);

    /**
     * Get model metadata
     *
     * @return Model ID
     */
    String getModelId();

    /**
     * Get model version
     *
     * @return Model version
     */
    String getModelVersion();

    /**
     * Get model type
     *
     * @return Model type (e.g., RANDOM_FOREST, NEURAL_NETWORK)
     */
    String getModelType();

    /**
     * Check if model is ready for predictions
     *
     * @return true if model is loaded and ready
     */
    boolean isReady();

    /**
     * Get model performance metrics
     *
     * @return Performance metrics (accuracy, precision, recall, etc.)
     */
    java.util.Map<String, Double> getPerformanceMetrics();

    /**
     * Warm up the model (load into memory, initialize)
     */
    void warmUp();

    /**
     * Reload model from disk/storage
     */
    void reload();
}
