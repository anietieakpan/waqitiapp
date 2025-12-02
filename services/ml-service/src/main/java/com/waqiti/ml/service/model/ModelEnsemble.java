package com.waqiti.ml.service.model;

import com.waqiti.ml.domain.TransactionFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ML Model Ensemble for Fraud Detection
 * 
 * Combines multiple machine learning models:
 * - Neural Network (Deep Learning)
 * - XGBoost (Gradient Boosting)
 * - Isolation Forest (Anomaly Detection)
 * - Random Forest (Ensemble)
 * - SVM (Support Vector Machine)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelEnsemble {
    
    private final NeuralNetworkModel neuralNetworkModel;
    private final XGBoostModel xgboostModel;
    private final IsolationForestModel isolationForestModel;
    private final RandomForestModel randomForestModel;
    private final SVMModel svmModel;
    
    // Model weights based on validation performance
    private static final Map<String, Double> MODEL_WEIGHTS = Map.of(
        "neural_network", 0.35,
        "xgboost", 0.30,
        "isolation_forest", 0.15,
        "random_forest", 0.15,
        "svm", 0.05
    );
    
    /**
     * Predicts fraud probability using ensemble of models
     */
    public double predict(TransactionFeatures features) {
        try {
            // Convert features to input arrays
            double[] inputFeatures = features.toDoubleArray();
            
            // Run all models in parallel
            CompletableFuture<Double> nnFuture = CompletableFuture.supplyAsync(() -> 
                neuralNetworkModel.predict(inputFeatures));
            
            CompletableFuture<Double> xgbFuture = CompletableFuture.supplyAsync(() -> 
                xgboostModel.predict(inputFeatures));
            
            CompletableFuture<Double> ifFuture = CompletableFuture.supplyAsync(() -> 
                isolationForestModel.predict(inputFeatures));
            
            CompletableFuture<Double> rfFuture = CompletableFuture.supplyAsync(() -> 
                randomForestModel.predict(inputFeatures));
            
            CompletableFuture<Double> svmFuture = CompletableFuture.supplyAsync(() -> 
                svmModel.predict(inputFeatures));
            
            // Wait for all models to complete with timeout
            try {
                CompletableFuture.allOf(nnFuture, xgbFuture, ifFuture, rfFuture, svmFuture)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("ML model ensemble timed out after 5 seconds", e);
                throw new RuntimeException("Model ensemble prediction timed out", e);
            } catch (Exception e) {
                log.error("ML model ensemble failed", e);
                throw new RuntimeException("Model ensemble prediction failed", e);
            }

            // Get individual predictions (already completed, safe to get immediately)
            double nnScore, xgbScore, ifScore, rfScore, svmScore;
            try {
                nnScore = nnFuture.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                xgbScore = xgbFuture.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                ifScore = ifFuture.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                rfScore = rfFuture.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                svmScore = svmFuture.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Failed to retrieve ML model predictions", e);
                throw new RuntimeException("Failed to retrieve model predictions", e);
            }
            
            // Calculate weighted ensemble score
            double ensembleScore = 
                nnScore * MODEL_WEIGHTS.get("neural_network") +
                xgbScore * MODEL_WEIGHTS.get("xgboost") +
                ifScore * MODEL_WEIGHTS.get("isolation_forest") +
                rfScore * MODEL_WEIGHTS.get("random_forest") +
                svmScore * MODEL_WEIGHTS.get("svm");
            
            // Apply ensemble calibration
            double calibratedScore = calibrateScore(ensembleScore, features);
            
            // Log prediction details
            log.debug("Model ensemble prediction - NN: {}, XGB: {}, IF: {}, RF: {}, SVM: {}, Final: {}", 
                     nnScore, xgbScore, ifScore, rfScore, svmScore, calibratedScore);
            
            return Math.min(1.0, Math.max(0.0, calibratedScore));
            
        } catch (Exception e) {
            log.error("Error in model ensemble prediction", e);
            // Return conservative high score on error
            return 0.75;
        }
    }
    
    /**
     * Gets feature importance from the ensemble
     */
    public Map<String, Double> getFeatureImportance(TransactionFeatures features) {
        Map<String, Double> importance = new HashMap<>();
        String[] featureNames = TransactionFeatures.getFeatureNames();
        
        try {
            // Get feature importance from tree-based models
            Map<String, Double> xgbImportance = xgboostModel.getFeatureImportance();
            Map<String, Double> rfImportance = randomForestModel.getFeatureImportance();
            
            // Combine importance scores
            for (String featureName : featureNames) {
                double xgbImp = xgbImportance.getOrDefault(featureName, 0.0);
                double rfImp = rfImportance.getOrDefault(featureName, 0.0);
                
                // Weighted average of importance scores
                double combinedImportance = (xgbImp * 0.6) + (rfImp * 0.4);
                importance.put(featureName, combinedImportance);
            }
            
            return importance;
            
        } catch (Exception e) {
            log.error("Error calculating feature importance", e);
            return createDefaultImportance(featureNames);
        }
    }
    
    /**
     * Calibrates ensemble score based on business rules and historical performance
     */
    private double calibrateScore(double rawScore, TransactionFeatures features) {
        // Apply business logic calibration
        double calibratedScore = rawScore;
        
        // High-value transaction adjustment
        if (features.getAmount() > 10000.0) {
            calibratedScore = Math.min(1.0, calibratedScore * 1.2);
        }
        
        // New user adjustment
        if (features.getAccountAgeInDays() < 30) {
            calibratedScore = Math.min(1.0, calibratedScore * 1.1);
        }
        
        // Cross-border transaction adjustment
        if (features.isCrossBorderTransaction()) {
            calibratedScore = Math.min(1.0, calibratedScore * 1.15);
        }
        
        // Verified user adjustment (lower risk)
        if (features.isVerifiedUser() && features.isWhitelistMatch()) {
            calibratedScore = calibratedScore * 0.8;
        }
        
        // VPN detected adjustment
        if (features.isVpnDetected()) {
            calibratedScore = Math.min(1.0, calibratedScore * 1.3);
        }
        
        // Time-based adjustments
        if (!features.isBusinessHours() && features.getAmount() > 5000.0) {
            calibratedScore = Math.min(1.0, calibratedScore * 1.1);
        }
        
        return calibratedScore;
    }
    
    /**
     * Creates default feature importance when calculation fails
     */
    private Map<String, Double> createDefaultImportance(String[] featureNames) {
        Map<String, Double> defaultImportance = new HashMap<>();
        
        // Assign higher importance to known critical features
        Map<String, Double> criticalFeatures = Map.of(
            "amount", 0.15,
            "velocityScore", 0.12,
            "userAge", 0.08,
            "isNewDevice", 0.08,
            "accountAgeInDays", 0.07,
            "behaviorConsistencyScore", 0.06,
            "networkRiskScore", 0.05
        );
        
        for (String featureName : featureNames) {
            defaultImportance.put(featureName, 
                criticalFeatures.getOrDefault(featureName, 0.01));
        }
        
        return defaultImportance;
    }
    
    /**
     * Validates model health and performance
     */
    public ModelHealthStatus validateModels() {
        ModelHealthStatus status = new ModelHealthStatus();
        
        try {
            // Test each model with synthetic data
            double[] testFeatures = generateTestFeatures();
            
            status.neuralNetworkHealthy = testModel(() -> neuralNetworkModel.predict(testFeatures));
            status.xgboostHealthy = testModel(() -> xgboostModel.predict(testFeatures));
            status.isolationForestHealthy = testModel(() -> isolationForestModel.predict(testFeatures));
            status.randomForestHealthy = testModel(() -> randomForestModel.predict(testFeatures));
            status.svmHealthy = testModel(() -> svmModel.predict(testFeatures));
            
            status.overallHealthy = status.neuralNetworkHealthy && status.xgboostHealthy && 
                                   status.isolationForestHealthy && status.randomForestHealthy && 
                                   status.svmHealthy;
            
        } catch (Exception e) {
            log.error("Error validating model health", e);
            status.overallHealthy = false;
        }
        
        return status;
    }
    
    private boolean testModel(ModelTest test) {
        try {
            double result = test.test();
            return result >= 0.0 && result <= 1.0; // Valid probability range
        } catch (Exception e) {
            log.error("Model test failed", e);
            return false;
        }
    }
    
    private double[] generateTestFeatures() {
        // Generate realistic test feature vector
        double[] features = new double[TransactionFeatures.getFeatureNames().length];
        Random random = ThreadLocalRandom.current();
        
        for (int i = 0; i < features.length; i++) {
            features[i] = random.nextDouble();
        }
        
        return features;
    }
    
    @FunctionalInterface
    private interface ModelTest {
        double test() throws Exception;
    }
    
    public static class ModelHealthStatus {
        public boolean neuralNetworkHealthy;
        public boolean xgboostHealthy;
        public boolean isolationForestHealthy;
        public boolean randomForestHealthy;
        public boolean svmHealthy;
        public boolean overallHealthy;
        
        @Override
        public String toString() {
            return String.format("ModelHealth{NN: %s, XGB: %s, IF: %s, RF: %s, SVM: %s, Overall: %s}",
                neuralNetworkHealthy, xgboostHealthy, isolationForestHealthy, 
                randomForestHealthy, svmHealthy, overallHealthy);
        }
    }
}