package com.waqiti.ml.service;

import com.waqiti.ml.dto.TransactionData;
import com.waqiti.ml.entity.UserBehaviorProfile;
import com.waqiti.ml.entity.BehaviorMetrics;
import com.waqiti.ml.config.MLModelConfiguration;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import org.tensorflow.*;
import org.tensorflow.framework.SavedModelBundle;
import org.tensorflow.types.TFloat32;
import org.tensorflow.ndarray.buffer.FloatDataBuffer;
import org.tensorflow.ndarray.Shape;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production-ready Machine Learning Model Service with TensorFlow integration.
 * Provides fraud detection, anomaly detection, and risk scoring using ML models.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MachineLearningModelService {

    private final MLModelConfiguration modelConfig;
    private final ExecutorService mlExecutor = Executors.newFixedThreadPool(4);

    @Value("${ml.models.fraud-detection.path:/models/fraud-detection}")
    private String fraudModelPath;

    @Value("${ml.models.anomaly-detection.path:/models/anomaly-detection}")
    private String anomalyModelPath;

    @Value("${ml.models.risk-scoring.path:/models/risk-scoring}")
    private String riskScoringModelPath;

    @Value("${ml.models.clustering.path:/models/clustering}")
    private String clusteringModelPath;

    @Value("${ml.feature.scaling.enabled:true}")
    private boolean featureScalingEnabled;

    // Model instances
    private SavedModelBundle fraudDetectionModel;
    private SavedModelBundle anomalyDetectionModel;
    private SavedModelBundle riskScoringModel;
    private SavedModelBundle clusteringModel;

    // Model metadata
    private final Map<String, String> modelVersions = new ConcurrentHashMap<>();
    private final Map<String, Double> modelConfidences = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> modelLoadTimes = new ConcurrentHashMap<>();

    // Feature scaling parameters
    private final Map<String, Double> featureMeans = new ConcurrentHashMap<>();
    private final Map<String, Double> featureStdDevs = new ConcurrentHashMap<>();

    // Model performance metrics
    private final Map<String, Integer> predictionCounts = new ConcurrentHashMap<>();
    private final Map<String, Double> averageLatencies = new ConcurrentHashMap<>();

    private static final String CURRENT_MODEL_VERSION = "2.1.0";
    private static final int FEATURE_VECTOR_SIZE = 25;

    @PostConstruct
    public void initializeModels() {
        try {
            log.info("Initializing ML models...");
            
            // Load all models in parallel
            CompletableFuture<Void> fraudModelFuture = CompletableFuture.runAsync(() -> loadFraudDetectionModel());
            CompletableFuture<Void> anomalyModelFuture = CompletableFuture.runAsync(() -> loadAnomalyDetectionModel());
            CompletableFuture<Void> riskModelFuture = CompletableFuture.runAsync(() -> loadRiskScoringModel());
            CompletableFuture<Void> clusterModelFuture = CompletableFuture.runAsync(() -> loadClusteringModel());
            
            // Wait for all models to load
            CompletableFuture.allOf(fraudModelFuture, anomalyModelFuture, riskModelFuture, clusterModelFuture)
                .join();
            
            // Initialize feature scaling parameters
            initializeFeatureScaling();
            
            log.info("ML models initialized successfully. Version: {}", CURRENT_MODEL_VERSION);
            
        } catch (Exception e) {
            log.error("Failed to initialize ML models", e);
            throw new MLProcessingException("ML model initialization failed", e);
        }
    }

    /**
     * Predict fraud risk using ensemble of ML models
     */
    @Traced(operation = "fraud_risk_prediction")
    public double predictFraudRisk(TransactionData transaction, UserBehaviorProfile profile) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Create feature vector
            double[] features = createComprehensiveFeatureVector(transaction, profile);
            
            // Scale features if enabled
            if (featureScalingEnabled) {
                features = scaleFeatures(features);
            }
            
            // Run ensemble prediction
            double fraudProbability = runFraudDetectionModel(features);
            double riskScore = runRiskScoringModel(features);
            double anomalyScore = runAnomalyDetectionModel(features);
            
            // Ensemble combination with weights
            double combinedScore = (fraudProbability * 0.5) + (riskScore * 0.3) + (anomalyScore * 0.2);
            
            // Update model performance metrics
            updateModelMetrics("fraud_prediction", startTime);
            
            log.debug("Fraud risk prediction completed: {} (fraud: {}, risk: {}, anomaly: {})", 
                combinedScore, fraudProbability, riskScore, anomalyScore);
            
            return Math.min(combinedScore * 100.0, 100.0);
            
        } catch (Exception e) {
            log.error("Error in fraud risk prediction for transaction: {}", transaction.getTransactionId(), e);
            return 50.0; // Conservative score on error
        }
    }

    /**
     * Detect anomalies using isolation forest and statistical methods
     */
    @Traced(operation = "anomaly_detection")
    public double detectAnomalies(double[] featureVector) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (anomalyDetectionModel == null) {
                log.warn("Anomaly detection model not loaded, using statistical fallback");
                return detectStatisticalAnomalies(featureVector);
            }
            
            // Prepare input tensor
            try (TFloat32 inputTensor = TFloat32.tensorOf(Shape.of(1, featureVector.length))) {
                FloatDataBuffer buffer = inputTensor.asRawTensor().data().asFloats();
                for (int i = 0; i < featureVector.length; i++) {
                    buffer.setFloat(i, (float) featureVector[i]);
                }
                
                // Run inference
                try (Session session = anomalyDetectionModel.session()) {
                    Map<String, Tensor> feedDict = Map.of("input_features", inputTensor);
                    List<Tensor> outputs = session.run(feedDict, List.of("anomaly_score"));
                    
                    try (Tensor output = outputs.get(0)) {
                        float anomalyScore = output.asRawTensor().data().asFloats().getFloat(0);
                        
                        updateModelMetrics("anomaly_detection", startTime);
                        return Math.min(Math.max(anomalyScore, 0.0), 1.0);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error in anomaly detection, using statistical fallback", e);
            return detectStatisticalAnomalies(featureVector);
        }
    }

    /**
     * Get current model version
     */
    public String getCurrentModelVersion() {
        return CURRENT_MODEL_VERSION;
    }

    /**
     * Get model confidence score
     */
    public double getModelConfidence() {
        return modelConfidences.getOrDefault("ensemble", 0.85);
    }

    /**
     * Perform user clustering for similarity analysis
     */
    @Traced(operation = "user_clustering")
    public String performUserClustering(UserBehaviorProfile profile) {
        try {
            if (clusteringModel == null) {
                return performSimpleRuleBasedClustering(profile);
            }
            
            double[] features = createUserFeatureVector(profile);
            
            // Run clustering model
            try (TFloat32 inputTensor = TFloat32.tensorOf(Shape.of(1, features.length))) {
                FloatDataBuffer buffer = inputTensor.asRawTensor().data().asFloats();
                for (int i = 0; i < features.length; i++) {
                    buffer.setFloat(i, (float) features[i]);
                }
                
                try (Session session = clusteringModel.session()) {
                    Map<String, Tensor> feedDict = Map.of("user_features", inputTensor);
                    List<Tensor> outputs = session.run(feedDict, List.of("cluster_id"));
                    
                    try (Tensor output = outputs.get(0)) {
                        int clusterId = (int) output.asRawTensor().data().asFloats().getFloat(0);
                        return "CLUSTER_" + clusterId;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error in user clustering for user: {}", profile.getUserId(), e);
            return performSimpleRuleBasedClustering(profile);
        }
    }

    /**
     * Load fraud detection model
     */
    private void loadFraudDetectionModel() {
        try {
            Path modelPath = Paths.get(fraudModelPath);
            if (modelPath.toFile().exists()) {
                fraudDetectionModel = SavedModelBundle.load(fraudModelPath, "serve");
                modelVersions.put("fraud_detection", CURRENT_MODEL_VERSION);
                modelConfidences.put("fraud_detection", 0.92);
                modelLoadTimes.put("fraud_detection", LocalDateTime.now());
                log.info("Fraud detection model loaded successfully from: {}", fraudModelPath);
            } else {
                log.warn("Fraud detection model not found at: {}, using rule-based fallback", fraudModelPath);
            }
        } catch (Exception e) {
            log.error("Failed to load fraud detection model", e);
        }
    }

    /**
     * Load anomaly detection model
     */
    private void loadAnomalyDetectionModel() {
        try {
            Path modelPath = Paths.get(anomalyModelPath);
            if (modelPath.toFile().exists()) {
                anomalyDetectionModel = SavedModelBundle.load(anomalyModelPath, "serve");
                modelVersions.put("anomaly_detection", CURRENT_MODEL_VERSION);
                modelConfidences.put("anomaly_detection", 0.88);
                modelLoadTimes.put("anomaly_detection", LocalDateTime.now());
                log.info("Anomaly detection model loaded successfully from: {}", anomalyModelPath);
            } else {
                log.warn("Anomaly detection model not found at: {}, using statistical fallback", anomalyModelPath);
            }
        } catch (Exception e) {
            log.error("Failed to load anomaly detection model", e);
        }
    }

    /**
     * Load risk scoring model
     */
    private void loadRiskScoringModel() {
        try {
            Path modelPath = Paths.get(riskScoringModelPath);
            if (modelPath.toFile().exists()) {
                riskScoringModel = SavedModelBundle.load(riskScoringModelPath, "serve");
                modelVersions.put("risk_scoring", CURRENT_MODEL_VERSION);
                modelConfidences.put("risk_scoring", 0.90);
                modelLoadTimes.put("risk_scoring", LocalDateTime.now());
                log.info("Risk scoring model loaded successfully from: {}", riskScoringModelPath);
            } else {
                log.warn("Risk scoring model not found at: {}, using heuristic fallback", riskScoringModelPath);
            }
        } catch (Exception e) {
            log.error("Failed to load risk scoring model", e);
        }
    }

    /**
     * Load clustering model
     */
    private void loadClusteringModel() {
        try {
            Path modelPath = Paths.get(clusteringModelPath);
            if (modelPath.toFile().exists()) {
                clusteringModel = SavedModelBundle.load(clusteringModelPath, "serve");
                modelVersions.put("clustering", CURRENT_MODEL_VERSION);
                modelConfidences.put("clustering", 0.85);
                modelLoadTimes.put("clustering", LocalDateTime.now());
                log.info("Clustering model loaded successfully from: {}", clusteringModelPath);
            } else {
                log.warn("Clustering model not found at: {}, using rule-based fallback", clusteringModelPath);
            }
        } catch (Exception e) {
            log.error("Failed to load clustering model", e);
        }
    }

    /**
     * Initialize feature scaling parameters
     */
    private void initializeFeatureScaling() {
        // Default feature scaling parameters (would be loaded from model metadata in production)
        featureMeans.put("amount", 500.0);
        featureMeans.put("hour", 12.0);
        featureMeans.put("day_of_week", 4.0);
        featureMeans.put("transaction_count", 50.0);
        featureMeans.put("velocity", 2.0);

        featureStdDevs.put("amount", 1000.0);
        featureStdDevs.put("hour", 7.0);
        featureStdDevs.put("day_of_week", 2.0);
        featureStdDevs.put("transaction_count", 30.0);
        featureStdDevs.put("velocity", 5.0);
    }

    /**
     * Create comprehensive feature vector for ML models
     */
    private double[] createComprehensiveFeatureVector(TransactionData transaction, UserBehaviorProfile profile) {
        double[] features = new double[FEATURE_VECTOR_SIZE];
        int index = 0;

        // Transaction features
        features[index++] = transaction.getAmount().doubleValue();
        features[index++] = transaction.getTimestamp().getHour();
        features[index++] = transaction.getTimestamp().getDayOfWeek().getValue();
        features[index++] = transaction.getTimestamp().getDayOfMonth();
        features[index++] = transaction.getTimestamp().getMonthValue();

        // User behavior features
        features[index++] = profile.getTotalTransactionCount().doubleValue();
        features[index++] = profile.getRiskScore();
        features[index++] = profile.getConfidenceScore();

        // Behavioral metrics
        if (profile.getBehaviorMetrics() != null) {
            BehaviorMetrics metrics = profile.getBehaviorMetrics();
            features[index++] = metrics.getAverageAmount() != null ? metrics.getAverageAmount().doubleValue() : 0.0;
            features[index++] = metrics.getAverageHourlyTransactionRate();
            features[index++] = metrics.getAverageDailyTransactionRate();
            features[index++] = metrics.getSuccessRate();
            features[index++] = metrics.getUniqueRecipientsCount().doubleValue();
            features[index++] = metrics.getDiversityScore();
        } else {
            // Fill with defaults if no metrics available
            for (int i = 0; i < 6; i++) features[index++] = 0.0;
        }

        // Transaction type encoding (one-hot)
        features[index++] = "P2P_TRANSFER".equals(transaction.getTransactionType()) ? 1.0 : 0.0;
        features[index++] = "PAYMENT".equals(transaction.getTransactionType()) ? 1.0 : 0.0;
        features[index++] = "WITHDRAWAL".equals(transaction.getTransactionType()) ? 1.0 : 0.0;
        features[index++] = "DEPOSIT".equals(transaction.getTransactionType()) ? 1.0 : 0.0;
        features[index++] = "INTERNATIONAL".equals(transaction.getTransactionType()) ? 1.0 : 0.0;

        // Device and network features
        features[index++] = transaction.isFromTrustedDevice() ? 1.0 : 0.0;
        features[index++] = transaction.isFromSuspiciousNetwork() ? 1.0 : 0.0;
        features[index++] = transaction.getAuthenticationStrengthScore();

        // Geographic features
        features[index++] = transaction.isInternational() ? 1.0 : 0.0;
        features[index++] = transaction.isCrypto() ? 1.0 : 0.0;
        features[index++] = transaction.isHighValue() ? 1.0 : 0.0;

        return features;
    }

    /**
     * Create user-specific feature vector for clustering
     */
    private double[] createUserFeatureVector(UserBehaviorProfile profile) {
        double[] features = new double[15];
        int index = 0;

        features[index++] = profile.getTotalTransactionCount().doubleValue();
        features[index++] = profile.getRiskScore();
        features[index++] = profile.getProfileAgeDays();

        if (profile.getBehaviorMetrics() != null) {
            BehaviorMetrics metrics = profile.getBehaviorMetrics();
            features[index++] = metrics.getAverageAmount() != null ? metrics.getAverageAmount().doubleValue() : 0.0;
            features[index++] = metrics.getAverageHourlyTransactionRate();
            features[index++] = metrics.getAverageDailyTransactionRate();
            features[index++] = metrics.getSuccessRate();
            features[index++] = metrics.getUniqueRecipientsCount().doubleValue();
            features[index++] = metrics.getUniqueMerchantsCount().doubleValue();
            features[index++] = metrics.getDiversityScore();
            features[index++] = metrics.getWeekendTransactionCount().doubleValue();
            features[index++] = metrics.getNightTransactionCount().doubleValue();
            features[index++] = metrics.getInternationalTransactionCount().doubleValue();
            features[index++] = metrics.getCryptoTransactionCount().doubleValue();
        } else {
            for (int i = 0; i < 11; i++) features[index++] = 0.0;
        }

        features[index++] = profile.getConfidenceScore();

        return features;
    }

    /**
     * Scale features using z-score normalization
     */
    private double[] scaleFeatures(double[] features) {
        double[] scaledFeatures = new double[features.length];
        
        for (int i = 0; i < features.length; i++) {
            String featureKey = "feature_" + i;
            double mean = featureMeans.getOrDefault(featureKey, 0.0);
            double stdDev = featureStdDevs.getOrDefault(featureKey, 1.0);
            
            scaledFeatures[i] = stdDev != 0 ? (features[i] - mean) / stdDev : features[i];
        }
        
        return scaledFeatures;
    }

    /**
     * Run fraud detection model inference
     */
    private double runFraudDetectionModel(double[] features) {
        if (fraudDetectionModel == null) {
            return runHeuristicFraudDetection(features);
        }

        try (TFloat32 inputTensor = TFloat32.tensorOf(Shape.of(1, features.length))) {
            FloatDataBuffer buffer = inputTensor.asRawTensor().data().asFloats();
            for (int i = 0; i < features.length; i++) {
                buffer.setFloat(i, (float) features[i]);
            }

            try (Session session = fraudDetectionModel.session()) {
                Map<String, Tensor> feedDict = Map.of("transaction_features", inputTensor);
                List<Tensor> outputs = session.run(feedDict, List.of("fraud_probability"));

                try (Tensor output = outputs.get(0)) {
                    return output.asRawTensor().data().asFloats().getFloat(0);
                }
            }
        } catch (Exception e) {
            log.error("Error running fraud detection model, using heuristic fallback", e);
            return runHeuristicFraudDetection(features);
        }
    }

    /**
     * Run risk scoring model inference
     */
    private double runRiskScoringModel(double[] features) {
        if (riskScoringModel == null) {
            return runHeuristicRiskScoring(features);
        }

        try (TFloat32 inputTensor = TFloat32.tensorOf(Shape.of(1, features.length))) {
            FloatDataBuffer buffer = inputTensor.asRawTensor().data().asFloats();
            for (int i = 0; i < features.length; i++) {
                buffer.setFloat(i, (float) features[i]);
            }

            try (Session session = riskScoringModel.session()) {
                Map<String, Tensor> feedDict = Map.of("risk_features", inputTensor);
                List<Tensor> outputs = session.run(feedDict, List.of("risk_score"));

                try (Tensor output = outputs.get(0)) {
                    return output.asRawTensor().data().asFloats().getFloat(0);
                }
            }
        } catch (Exception e) {
            log.error("Error running risk scoring model, using heuristic fallback", e);
            return runHeuristicRiskScoring(features);
        }
    }

    /**
     * Run anomaly detection model inference
     */
    private double runAnomalyDetectionModel(double[] features) {
        if (anomalyDetectionModel == null) {
            return detectStatisticalAnomalies(features);
        }

        try (TFloat32 inputTensor = TFloat32.tensorOf(Shape.of(1, features.length))) {
            FloatDataBuffer buffer = inputTensor.asRawTensor().data().asFloats();
            for (int i = 0; i < features.length; i++) {
                buffer.setFloat(i, (float) features[i]);
            }

            try (Session session = anomalyDetectionModel.session()) {
                Map<String, Tensor> feedDict = Map.of("anomaly_features", inputTensor);
                List<Tensor> outputs = session.run(feedDict, List.of("anomaly_score"));

                try (Tensor output = outputs.get(0)) {
                    return output.asRawTensor().data().asFloats().getFloat(0);
                }
            }
        } catch (Exception e) {
            log.error("Error running anomaly detection model", e);
            return detectStatisticalAnomalies(features);
        }
    }

    /**
     * Heuristic fraud detection fallback
     */
    private double runHeuristicFraudDetection(double[] features) {
        double riskScore = 0.0;
        
        // Amount-based heuristics
        double amount = features[0];
        if (amount > 10000) riskScore += 0.3;
        else if (amount > 5000) riskScore += 0.2;
        else if (amount > 1000) riskScore += 0.1;
        
        // Time-based heuristics
        double hour = features[1];
        if (hour < 6 || hour > 22) riskScore += 0.2;
        
        // Transaction count heuristics
        double transactionCount = features[5];
        if (transactionCount == 0) riskScore += 0.2; // New user
        
        return Math.min(riskScore, 1.0);
    }

    /**
     * Heuristic risk scoring fallback
     */
    private double runHeuristicRiskScoring(double[] features) {
        double riskScore = 0.0;
        
        // High amount transactions
        if (features[0] > 5000) riskScore += 0.4;
        
        // Unusual time patterns
        if (features[1] < 6 || features[1] > 22) riskScore += 0.2;
        
        // Low transaction history
        if (features[5] < 10) riskScore += 0.3;
        
        // Weekend transactions
        if (features[2] == 6 || features[2] == 7) riskScore += 0.1;
        
        return Math.min(riskScore, 1.0);
    }

    /**
     * Statistical anomaly detection fallback
     */
    private double detectStatisticalAnomalies(double[] features) {
        double anomalyScore = 0.0;
        
        // Check for outliers using IQR method
        for (double feature : features) {
            if (Math.abs(feature) > 3.0) { // 3 standard deviations
                anomalyScore += 0.1;
            }
        }
        
        return Math.min(anomalyScore, 1.0);
    }

    /**
     * Simple rule-based clustering fallback
     */
    private String performSimpleRuleBasedClustering(UserBehaviorProfile profile) {
        double riskScore = profile.getRiskScore();
        long transactionCount = profile.getTotalTransactionCount();
        
        if (riskScore >= 80) return "HIGH_RISK_CLUSTER";
        if (riskScore >= 60) return "MEDIUM_HIGH_RISK_CLUSTER";
        if (riskScore >= 40) return "MEDIUM_RISK_CLUSTER";
        if (riskScore >= 20) return "LOW_RISK_CLUSTER";
        
        if (transactionCount > 1000) return "HIGH_VOLUME_CLUSTER";
        if (transactionCount > 100) return "MEDIUM_VOLUME_CLUSTER";
        
        return "LOW_ACTIVITY_CLUSTER";
    }

    /**
     * Update model performance metrics
     */
    private void updateModelMetrics(String modelName, long startTime) {
        long latency = System.currentTimeMillis() - startTime;
        
        predictionCounts.merge(modelName, 1, Integer::sum);
        
        double currentAvg = averageLatencies.getOrDefault(modelName, 0.0);
        int count = predictionCounts.get(modelName);
        double newAvg = ((currentAvg * (count - 1)) + latency) / count;
        averageLatencies.put(modelName, newAvg);
    }

    /**
     * Get model performance statistics
     */
    @Cacheable(value = "modelStats", key = "'performance'")
    public Map<String, Object> getModelPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("model_versions", modelVersions);
        stats.put("model_confidences", modelConfidences);
        stats.put("model_load_times", modelLoadTimes);
        stats.put("prediction_counts", predictionCounts);
        stats.put("average_latencies", averageLatencies);
        stats.put("current_version", CURRENT_MODEL_VERSION);
        return stats;
    }

    /**
     * Reload specific model
     */
    public void reloadModel(String modelType) {
        try {
            switch (modelType.toLowerCase()) {
                case "fraud":
                    if (fraudDetectionModel != null) fraudDetectionModel.close();
                    loadFraudDetectionModel();
                    break;
                case "anomaly":
                    if (anomalyDetectionModel != null) anomalyDetectionModel.close();
                    loadAnomalyDetectionModel();
                    break;
                case "risk":
                    if (riskScoringModel != null) riskScoringModel.close();
                    loadRiskScoringModel();
                    break;
                case "clustering":
                    if (clusteringModel != null) clusteringModel.close();
                    loadClusteringModel();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown model type: " + modelType);
            }
            log.info("Model {} reloaded successfully", modelType);
        } catch (Exception e) {
            log.error("Failed to reload model: {}", modelType, e);
            throw new MLProcessingException("Model reload failed", e);
        }
    }

    /**
     * Health check for all models
     */
    public Map<String, Boolean> checkModelHealth() {
        Map<String, Boolean> health = new HashMap<>();
        
        health.put("fraud_detection", fraudDetectionModel != null);
        health.put("anomaly_detection", anomalyDetectionModel != null);
        health.put("risk_scoring", riskScoringModel != null);
        health.put("clustering", clusteringModel != null);
        
        return health;
    }

    /**
     * Get model recommendations for improvement
     */
    public List<String> getModelRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        // Check model availability
        if (fraudDetectionModel == null) {
            recommendations.add("Load fraud detection model for improved accuracy");
        }
        if (anomalyDetectionModel == null) {
            recommendations.add("Load anomaly detection model for better outlier detection");
        }
        
        // Check performance metrics
        double avgLatency = averageLatencies.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (avgLatency > 500) {
            recommendations.add("Consider model optimization for latency improvement");
        }
        
        // Check prediction counts for model staleness
        int totalPredictions = predictionCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalPredictions > 100000) {
            recommendations.add("Consider model retraining with latest data");
        }
        
        return recommendations;
    }

    @PreDestroy
    public void cleanup() {
        try {
            log.info("Cleaning up ML models...");
            
            if (fraudDetectionModel != null) fraudDetectionModel.close();
            if (anomalyDetectionModel != null) anomalyDetectionModel.close();
            if (riskScoringModel != null) riskScoringModel.close();
            if (clusteringModel != null) clusteringModel.close();
            
            mlExecutor.shutdown();
            
            log.info("ML models cleanup completed");
            
        } catch (Exception e) {
            log.error("Error during ML models cleanup", e);
        }
    }
}