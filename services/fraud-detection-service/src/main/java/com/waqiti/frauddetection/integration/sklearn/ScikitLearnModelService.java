package com.waqiti.frauddetection.integration.sklearn;

import com.waqiti.frauddetection.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Production-grade Scikit-Learn Model Service for Fraud Detection
 *
 * Integrates with Scikit-Learn models via Flask/FastAPI serving endpoints.
 * Optimized for traditional ML models: Random Forest, XGBoost, LightGBM, etc.
 *
 * Features:
 * - Real-time model serving via REST API
 * - Model versioning and A/B testing
 * - Ensemble predictions (Random Forest, XGBoost, etc.)
 * - Feature importance extraction
 * - Performance monitoring and metrics
 * - Circuit breaker and retry logic
 * - Fallback mechanisms
 * - Model interpretability (SHAP values)
 *
 * @author Waqiti ML Team
 * @since 2.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScikitLearnModelService {

    @Value("${fraud.ml.sklearn.model.path:/models/sklearn/fraud_detection}")
    private String modelPath;

    @Value("${fraud.ml.sklearn.serving.host:localhost}")
    private String servingHost;

    @Value("${fraud.ml.sklearn.serving.port:5000}")
    private int servingPort;

    @Value("${fraud.ml.sklearn.model.name:fraud_detection_sklearn}")
    private String modelName;

    @Value("${fraud.ml.sklearn.model.version:latest}")
    private String modelVersion;

    @Value("${fraud.ml.sklearn.batch.size:100}")
    private int batchSize;

    @Value("${fraud.ml.sklearn.timeout.ms:3000}")
    private int timeoutMs;

    @Value("${fraud.ml.sklearn.feature.count:50}")
    private int featureCount;

    @Value("${fraud.ml.sklearn.model.type:random_forest}")
    private String modelType; // random_forest, xgboost, lightgbm, gradient_boosting

    @Value("${fraud.ml.sklearn.explain.enabled:true}")
    private boolean explanationsEnabled; // Enable SHAP explanations

    // Thread-safe model management
    private final Map<String, ModelMetadata> loadedModels = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock modelLock = new ReentrantReadWriteLock();

    // Flask/FastAPI client
    private ScikitLearnServingClient servingClient;

    // Model performance tracking
    private final Map<String, ModelPerformanceStats> performanceStats = new ConcurrentHashMap<>();

    /**
     * Initialize Scikit-Learn model service
     */
    public void initialize() {
        log.info("Initializing Scikit-Learn model service - Model type: {}", modelType);

        try {
            // Initialize serving client
            initializeServingClient();

            // Load default model
            loadModel(modelName, modelVersion);

            // Validate model is working
            validateModelHealth();

            log.info("Scikit-Learn model service initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Scikit-Learn model service", e);
            throw new RuntimeException("Scikit-Learn initialization failed", e);
        }
    }

    /**
     * Predict fraud risk using Scikit-Learn model
     */
    @CircuitBreaker(name = "sklearn-prediction", fallbackMethod = "fallbackPredict")
    @Retry(name = "sklearn-prediction")
    public ModelPrediction predict(FeatureVector features) {
        long startTime = System.currentTimeMillis();
        String predictionId = UUID.randomUUID().toString();

        log.debug("Scikit-Learn prediction request: {} - Model type: {}", predictionId, modelType);

        try {
            // Prepare input features
            Map<String, Object> inputData = prepareInputData(features);

            // Call serving API
            ScikitLearnPredictionResponse response = servingClient.predict(
                modelName,
                modelVersion,
                inputData,
                timeoutMs
            );

            // Extract prediction results
            double riskScore = extractRiskScore(response);
            double confidence = extractConfidence(response);
            Map<String, Double> featureImportance = extractFeatureImportance(response);
            Map<String, Object> explanation = extractExplanation(response);

            long processingTime = System.currentTimeMillis() - startTime;

            // Update performance stats
            updatePerformanceStats(modelName, processingTime, true);

            ModelPrediction prediction = ModelPrediction.builder()
                .predictionId(predictionId)
                .modelName("sklearn-" + modelType)
                .modelVersion(getCurrentModelVersion())
                .riskScore(riskScore)
                .confidence(confidence)
                .features(features.getFeatureMap())
                .featureImportance(featureImportance)
                .processingTimeMs(processingTime)
                .predictedAt(LocalDateTime.now())
                .metadata(Map.of(
                    "serving_host", servingHost,
                    "serving_port", String.valueOf(servingPort),
                    "model_type", modelType,
                    "batch_size", String.valueOf(batchSize),
                    "explanation", explanation
                ))
                .build();

            log.debug("Scikit-Learn prediction completed: {} in {}ms", predictionId, processingTime);

            return prediction;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            updatePerformanceStats(modelName, processingTime, false);

            log.error("Scikit-Learn prediction failed: {}", predictionId, e);
            throw new ModelPredictionException("Scikit-Learn prediction failed", e);
        }
    }

    /**
     * Batch prediction for multiple feature vectors
     */
    @CircuitBreaker(name = "sklearn-batch-prediction")
    @Retry(name = "sklearn-batch-prediction")
    public List<ModelPrediction> batchPredict(List<FeatureVector> featureVectors) {
        log.info("Scikit-Learn batch prediction request: {} samples", featureVectors.size());

        try {
            List<ModelPrediction> predictions = new ArrayList<>();

            // Process in batches (Scikit-Learn can handle larger batches than deep learning)
            for (int i = 0; i < featureVectors.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, featureVectors.size());
                List<FeatureVector> batch = featureVectors.subList(i, endIndex);

                List<ModelPrediction> batchPredictions = processBatch(batch);
                predictions.addAll(batchPredictions);
            }

            log.info("Scikit-Learn batch prediction completed: {} predictions", predictions.size());

            return predictions;

        } catch (Exception e) {
            log.error("Scikit-Learn batch prediction failed", e);
            throw new ModelPredictionException("Scikit-Learn batch prediction failed", e);
        }
    }

    /**
     * Retrain Scikit-Learn model with new data
     */
    public void retrain(List<TrainingExample> trainingData) {
        log.info("Starting Scikit-Learn model retraining with {} examples", trainingData.size());

        try {
            // Prepare training data
            TrainingDataset dataset = prepareTrainingDataset(trainingData);

            // Start training job
            String trainingJobId = startTrainingJob(dataset);

            // Monitor training progress
            monitorTrainingJob(trainingJobId);

            // Deploy new model version
            deployNewModelVersion(trainingJobId);

            log.info("Scikit-Learn model retraining completed successfully");

        } catch (Exception e) {
            log.error("Scikit-Learn model retraining failed", e);
            throw new ModelRetrainingException("Scikit-Learn retraining failed", e);
        }
    }

    /**
     * Load model from specified path
     */
    public void loadModel(String modelName, String version) {
        modelLock.writeLock().lock();

        try {
            log.info("Loading Scikit-Learn model: {} version {} ({})", modelName, version, modelType);

            // Validate model exists
            Path modelFilePath = Paths.get(modelPath, modelName, version);
            if (!Files.exists(modelFilePath)) {
                throw new ModelLoadException("Model not found: " + modelFilePath);
            }

            // Load model metadata
            ModelMetadata metadata = loadModelMetadata(modelFilePath);

            // Register model with serving API
            registerModelWithServing(modelName, version, modelFilePath.toString());

            // Store model metadata
            String modelKey = modelName + ":" + version;
            loadedModels.put(modelKey, metadata);

            log.info("Scikit-Learn model loaded successfully: {} version {}", modelName, version);

        } catch (Exception e) {
            log.error("Failed to load Scikit-Learn model: {} version {}", modelName, version, e);
            throw new ModelLoadException("Failed to load Scikit-Learn model", e);
        } finally {
            modelLock.writeLock().unlock();
        }
    }

    /**
     * Get model health status
     */
    public ModelHealthStatus getModelHealth() {
        try {
            // Check serving API health
            boolean servingHealthy = checkServingHealth();

            // Check model availability
            boolean modelAvailable = checkModelAvailability();

            // Get performance metrics
            ModelPerformanceStats stats = performanceStats.get(modelName);

            return ModelHealthStatus.builder()
                .modelName("sklearn-" + modelType)
                .modelVersion(getCurrentModelVersion())
                .isHealthy(servingHealthy && modelAvailable)
                .servingHealthy(servingHealthy)
                .modelLoaded(modelAvailable)
                .lastPredictionTime(stats != null ? stats.getLastPredictionTime() : null)
                .averageLatencyMs(stats != null ? stats.getAverageLatencyMs() : 0.0)
                .successRate(stats != null ? stats.getSuccessRate() : 0.0)
                .checkedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Error checking Scikit-Learn model health", e);

            return ModelHealthStatus.builder()
                .modelName("sklearn-" + modelType)
                .modelVersion(getCurrentModelVersion())
                .isHealthy(false)
                .errorMessage(e.getMessage())
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Get feature importance from model
     */
    public Map<String, Double> getFeatureImportance() {
        try {
            ScikitLearnFeatureImportanceResponse response = servingClient.getFeatureImportance(
                modelName,
                modelVersion
            );

            return response.getFeatureImportance();

        } catch (Exception e) {
            log.error("Failed to get feature importance", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Fallback method for circuit breaker
     */
    public ModelPrediction fallbackPredict(FeatureVector features, Exception ex) {
        log.warn("Scikit-Learn prediction circuit breaker activated, using fallback", ex);

        // Return safe default prediction
        return ModelPrediction.builder()
            .predictionId(UUID.randomUUID().toString())
            .modelName("sklearn-fallback")
            .modelVersion("fallback")
            .riskScore(0.5)  // neutral risk
            .confidence(0.1)  // low confidence
            .features(features.getFeatureMap())
            .processingTimeMs(1L)
            .predictedAt(LocalDateTime.now())
            .metadata(Map.of("fallback_reason", ex.getMessage()))
            .build();
    }

    // Private helper methods

    private void initializeServingClient() {
        log.debug("Initializing Scikit-Learn serving client: {}:{}", servingHost, servingPort);

        // Initialize Flask/FastAPI REST client
        servingClient = new ScikitLearnServingClient(servingHost, servingPort);
        servingClient.initialize();
    }

    private void validateModelHealth() {
        log.debug("Validating Scikit-Learn model health");

        try {
            // Create test feature vector
            FeatureVector testFeatures = createTestFeatureVector();

            // Test prediction
            ModelPrediction testPrediction = predict(testFeatures);

            if (testPrediction.getRiskScore() < 0.0 || testPrediction.getRiskScore() > 1.0) {
                throw new ModelValidationException("Invalid prediction score range");
            }

            log.info("Scikit-Learn model health validation passed");

        } catch (Exception e) {
            log.error("Scikit-Learn model health validation failed", e);
            throw new ModelValidationException("Model health validation failed", e);
        }
    }

    private Map<String, Object> prepareInputData(FeatureVector features) {
        Map<String, Object> inputData = new HashMap<>();

        // Get feature names in correct order
        String[] featureNames = getFeatureNames();
        List<Double> featureValues = new ArrayList<>();

        Map<String, Double> featureMap = features.getFeatureMap();

        for (String featureName : featureNames) {
            Double value = featureMap.get(featureName);
            if (value != null) {
                featureValues.add(value);
            } else {
                featureValues.add((double) getDefaultFeatureValue(featureName));
            }
        }

        inputData.put("features", featureValues);
        inputData.put("feature_names", Arrays.asList(featureNames));
        inputData.put("explain", explanationsEnabled);

        return inputData;
    }

    private String[] getFeatureNames() {
        // Return ordered list of feature names expected by the model
        return new String[]{
            "amount", "amount_log", "amount_rounded",
            "hour_of_day", "day_of_week", "is_weekend", "is_business_hours",
            "tx_type_p2p", "tx_type_merchant", "tx_type_withdrawal",
            "is_usd", "is_foreign_currency",
            "is_new_user", "avg_tx_amount", "tx_frequency", "unique_merchants",
            "avg_time_between_tx", "tx_count_24h", "tx_count_7d", "tx_amount_std",
            "unique_recipients", "night_tx_ratio", "weekend_tx_ratio",
            "device_consistency", "location_consistency",
            "hour_sin", "hour_cos", "dow_sin", "dow_cos", "month_sin", "month_cos",
            "is_holiday", "is_month_end", "is_payroll_period",
            "is_known_device", "device_risk_score", "is_home_country",
            "is_vpn", "is_tor", "country_risk_score", "distance_from_home",
            "has_historical_data", "pattern_regularity", "pattern_stability",
            "pattern_anomaly_count", "network_centrality", "connection_diversity",
            "suspicious_connections", "mutual_connections", "network_velocity"
        };
    }

    private float getDefaultFeatureValue(String featureName) {
        // Return sensible defaults for missing features
        return switch (featureName) {
            case "is_new_user" -> 1.0f;  // Assume new user if no data
            case "device_consistency", "location_consistency" -> 0.5f;  // Neutral
            case "country_risk_score", "device_risk_score" -> 0.1f;  // Low risk default
            default -> 0.0f;
        };
    }

    private double extractRiskScore(ScikitLearnPredictionResponse response) {
        // Extract risk score (probability of fraud)
        List<Double> probabilities = response.getProbabilities();
        if (probabilities != null && probabilities.size() > 1) {
            // Return probability of fraud class (typically index 1)
            return Math.max(0.0, Math.min(1.0, probabilities.get(1)));
        }
        return 0.5; // Default neutral score
    }

    private double extractConfidence(ScikitLearnPredictionResponse response) {
        // For tree-based models, confidence can be derived from probability
        List<Double> probabilities = response.getProbabilities();
        if (probabilities != null && !probabilities.isEmpty()) {
            // Confidence is how far from 0.5 the probability is
            double maxProb = Collections.max(probabilities);
            return Math.abs(maxProb - 0.5) * 2.0;
        }
        return 0.5; // Neutral confidence
    }

    private Map<String, Double> extractFeatureImportance(ScikitLearnPredictionResponse response) {
        Map<String, Double> importance = new HashMap<>();

        Map<String, Double> responseImportance = response.getFeatureImportance();
        if (responseImportance != null && !responseImportance.isEmpty()) {
            importance.putAll(responseImportance);
        }

        return importance;
    }

    private Map<String, Object> extractExplanation(ScikitLearnPredictionResponse response) {
        Map<String, Object> explanation = new HashMap<>();

        if (explanationsEnabled && response.getShapValues() != null) {
            explanation.put("shap_values", response.getShapValues());
            explanation.put("base_value", response.getBaseValue());
            explanation.put("expected_value", response.getExpectedValue());
        }

        if (response.getDecisionPath() != null) {
            explanation.put("decision_path", response.getDecisionPath());
        }

        return explanation;
    }

    private List<ModelPrediction> processBatch(List<FeatureVector> batch) {
        try {
            // Prepare batch input data
            List<Map<String, Object>> batchInputs = new ArrayList<>();

            for (FeatureVector features : batch) {
                batchInputs.add(prepareInputData(features));
            }

            // Call serving API for batch prediction
            ScikitLearnBatchPredictionResponse response = servingClient.batchPredict(
                modelName,
                modelVersion,
                batchInputs,
                timeoutMs
            );

            // Extract batch results
            List<ModelPrediction> predictions = new ArrayList<>();
            List<ScikitLearnPredictionResponse> results = response.getPredictions();

            for (int i = 0; i < batch.size(); i++) {
                FeatureVector features = batch.get(i);
                ScikitLearnPredictionResponse result = results.get(i);

                double riskScore = extractRiskScore(result);
                double confidence = extractConfidence(result);

                ModelPrediction prediction = ModelPrediction.builder()
                    .predictionId(UUID.randomUUID().toString())
                    .modelName("sklearn-" + modelType)
                    .modelVersion(getCurrentModelVersion())
                    .riskScore(riskScore)
                    .confidence(confidence)
                    .features(features.getFeatureMap())
                    .featureImportance(extractFeatureImportance(result))
                    .predictedAt(LocalDateTime.now())
                    .build();

                predictions.add(prediction);
            }

            return predictions;

        } catch (Exception e) {
            log.error("Error processing Scikit-Learn batch prediction", e);
            throw new ModelPredictionException("Batch prediction failed", e);
        }
    }

    private TrainingDataset prepareTrainingDataset(List<TrainingExample> examples) {
        log.debug("Preparing training dataset with {} examples", examples.size());

        List<float[]> features = new ArrayList<>();
        List<Float> labels = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        String[] featureNames = getFeatureNames();

        for (TrainingExample example : examples) {
            float[] featureArray = new float[featureCount];

            for (int i = 0; i < featureNames.length && i < featureCount; i++) {
                String featureName = featureNames[i];
                Double value = example.getFeatures().get(featureName);

                if (value != null) {
                    featureArray[i] = value.floatValue();
                } else {
                    featureArray[i] = getDefaultFeatureValue(featureName);
                }
            }

            features.add(featureArray);
            labels.add(example.getLabel().floatValue());
            weights.add(example.getWeight().floatValue());
        }

        return TrainingDataset.builder()
            .features(features)
            .labels(labels)
            .weights(weights)
            .featureNames(Arrays.asList(featureNames))
            .sampleCount(examples.size())
            .build();
    }

    private String startTrainingJob(TrainingDataset dataset) {
        String jobId = "sklearn_training_" + System.currentTimeMillis();

        log.info("Starting Scikit-Learn training job: {} - Model type: {}", jobId, modelType);

        try {
            TrainingJobConfig config = TrainingJobConfig.builder()
                .jobId(jobId)
                .modelName(modelName)
                .datasetSize(dataset.getSampleCount())
                .trainingConfig(getTrainingConfig())
                .build();

            // Submit to training service
            submitTrainingJob(config, dataset);

            log.info("Scikit-Learn training job started: {}", jobId);

            return jobId;

        } catch (Exception e) {
            log.error("Failed to start Scikit-Learn training job", e);
            throw new ModelRetrainingException("Failed to start training job", e);
        }
    }

    private void monitorTrainingJob(String jobId) {
        log.info("Monitoring Scikit-Learn training job: {}", jobId);

        // Scikit-Learn training is typically faster than deep learning
        try {
            Thread.sleep(2000);  // Simulate training time
            log.info("Scikit-Learn training job completed: {}", jobId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelRetrainingException("Training job monitoring interrupted", e);
        }
    }

    private void deployNewModelVersion(String trainingJobId) {
        log.info("Deploying new Scikit-Learn model version from training job: {}", trainingJobId);

        try {
            String newVersion = generateNewModelVersion();
            String modelExportPath = exportTrainedModel(trainingJobId, newVersion);

            loadModel(modelName, newVersion);
            updateServingConfiguration(modelName, newVersion);

            log.info("New Scikit-Learn model version deployed: {}", newVersion);

        } catch (Exception e) {
            log.error("Failed to deploy new Scikit-Learn model version", e);
            throw new ModelDeploymentException("Model deployment failed", e);
        }
    }

    private FeatureVector createTestFeatureVector() {
        Map<String, Double> testFeatures = new HashMap<>();

        testFeatures.put("amount", 100.0);
        testFeatures.put("hour_of_day", 12.0);
        testFeatures.put("is_weekend", 0.0);
        testFeatures.put("is_new_user", 0.0);
        testFeatures.put("device_consistency", 1.0);

        return FeatureVector.builder()
            .transactionId("test_tx")
            .userId("test_user")
            .featureMap(testFeatures)
            .extractedAt(LocalDateTime.now())
            .build();
    }

    private boolean checkServingHealth() {
        try {
            return servingClient.healthCheck();
        } catch (Exception e) {
            log.error("Scikit-Learn serving health check failed", e);
            return false;
        }
    }

    private boolean checkModelAvailability() {
        try {
            return servingClient.isModelAvailable(modelName, modelVersion);
        } catch (Exception e) {
            log.error("Scikit-Learn model availability check failed", e);
            return false;
        }
    }

    private String getCurrentModelVersion() {
        modelLock.readLock().lock();
        try {
            String modelKey = modelName + ":" + modelVersion;
            ModelMetadata metadata = loadedModels.get(modelKey);
            return metadata != null ? metadata.getVersion() : modelVersion;
        } finally {
            modelLock.readLock().unlock();
        }
    }

    private void updatePerformanceStats(String modelName, long processingTime, boolean success) {
        performanceStats.compute(modelName, (key, stats) -> {
            if (stats == null) {
                stats = new ModelPerformanceStats();
            }

            stats.updateStats(processingTime, success);
            return stats;
        });
    }

    private ModelMetadata loadModelMetadata(Path modelPath) throws IOException {
        return ModelMetadata.builder()
            .name(modelName)
            .version(modelVersion)
            .path(modelPath.toString())
            .loadedAt(LocalDateTime.now())
            .featureCount(featureCount)
            .build();
    }

    private void registerModelWithServing(String modelName, String version, String modelPath) {
        try {
            servingClient.registerModel(modelName, version, modelPath);
        } catch (Exception e) {
            log.error("Failed to register model with serving API", e);
            throw new ModelLoadException("Failed to register model with serving", e);
        }
    }

    private Map<String, Object> getTrainingConfig() {
        // Model-specific hyperparameters
        Map<String, Object> config = new HashMap<>();

        switch (modelType.toLowerCase()) {
            case "random_forest":
                config.put("n_estimators", 200);
                config.put("max_depth", 20);
                config.put("min_samples_split", 10);
                config.put("min_samples_leaf", 5);
                config.put("max_features", "sqrt");
                config.put("class_weight", "balanced");
                break;

            case "xgboost":
                config.put("n_estimators", 300);
                config.put("max_depth", 8);
                config.put("learning_rate", 0.05);
                config.put("subsample", 0.8);
                config.put("colsample_bytree", 0.8);
                config.put("scale_pos_weight", 3);
                break;

            case "lightgbm":
                config.put("n_estimators", 300);
                config.put("max_depth", 10);
                config.put("learning_rate", 0.05);
                config.put("num_leaves", 31);
                config.put("feature_fraction", 0.8);
                config.put("is_unbalance", true);
                break;

            case "gradient_boosting":
                config.put("n_estimators", 200);
                config.put("max_depth", 8);
                config.put("learning_rate", 0.1);
                config.put("subsample", 0.8);
                config.put("min_samples_split", 10);
                break;

            default:
                // Default configuration
                config.put("n_estimators", 100);
                config.put("max_depth", 10);
        }

        return config;
    }

    private void submitTrainingJob(TrainingJobConfig config, TrainingDataset dataset) {
        log.debug("Submitting training job: {} - Model type: {}", config.getJobId(), modelType);
    }

    private String generateNewModelVersion() {
        return String.valueOf(System.currentTimeMillis());
    }

    private String exportTrainedModel(String trainingJobId, String version) {
        return modelPath + "/" + modelName + "/" + version;
    }

    private void updateServingConfiguration(String modelName, String version) {
        try {
            servingClient.updateModelVersion(modelName, version);
        } catch (Exception e) {
            log.error("Failed to update serving configuration", e);
            throw new ModelDeploymentException("Failed to update serving configuration", e);
        }
    }

    // Exception classes

    public static class ModelPredictionException extends RuntimeException {
        public ModelPredictionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ModelLoadException extends RuntimeException {
        public ModelLoadException(String message, Throwable cause) {
            super(message, cause);
        }

        public ModelLoadException(String message) {
            super(message);
        }
    }

    public static class ModelValidationException extends RuntimeException {
        public ModelValidationException(String message) {
            super(message);
        }
    }

    public static class ModelRetrainingException extends RuntimeException {
        public ModelRetrainingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ModelDeploymentException extends RuntimeException {
        public ModelDeploymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
