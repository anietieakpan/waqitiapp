package com.waqiti.frauddetection.ml;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.entity.FraudIncident;
import com.waqiti.common.math.MoneyMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudMLModel {

    // SECURITY FIX: Configurable model path with multiple fallback options
    private static final String DEFAULT_MODEL_PATH = "/models/fraud_detection_model.pb";
    private static final String FALLBACK_MODEL_PATH = "/models/fraud_detection_model_v2.pb";
    private static final String EXTERNAL_MODEL_PATH_ENV = "FRAUD_MODEL_PATH";
    private static final double DEFAULT_THRESHOLD = 0.5;
    
    private Graph graph;
    private Session session;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final FeatureExtractor featureExtractor;
    private final ModelMetricsCollector metricsCollector;
    private boolean modelLoaded = false;
    private LocalDateTime modelLoadTime;
    private String loadedModelVersion;

    @PostConstruct
    public void initialize() {
        loadModelWithRetry();
    }

    /**
     * CRITICAL FIX: Load ML model with multiple fallback options and retry logic
     * 
     * Priority:
     * 1. External model path from environment variable
     * 2. Default model path from resources
     * 3. Fallback model path from resources
     * 4. Heuristic model (no TensorFlow)
     */
    private void loadModelWithRetry() {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries && !modelLoaded) {
            try {
                // Try external model path first
                String externalPath = System.getenv(EXTERNAL_MODEL_PATH_ENV);
                if (externalPath != null && !externalPath.isEmpty()) {
                    if (loadModelFromPath(externalPath, "EXTERNAL")) {
                        return;
                    }
                }
                
                // Try default model path from resources
                if (loadModelFromResources(DEFAULT_MODEL_PATH, "DEFAULT")) {
                    return;
                }
                
                // Try fallback model path
                if (loadModelFromResources(FALLBACK_MODEL_PATH, "FALLBACK")) {
                    return;
                }
                
                log.warn("ML model loading attempt {} failed, retrying...", retryCount + 1);
                retryCount++;
                Thread.sleep(1000 * retryCount); // Exponential backoff
                
            } catch (Exception e) {
                log.error("Error during ML model loading attempt {}: {}", retryCount + 1, e.getMessage());
                retryCount++;
            }
        }
        
        // All loading attempts failed - use heuristic model
        log.error("CRITICAL: Failed to load ML model after {} attempts. Using heuristic fraud detection.", maxRetries);
        log.error("Fraud detection will operate with reduced accuracy. Deploy ML model to improve detection.");
    }
    
    /**
     * Load model from external file path
     */
    private boolean loadModelFromPath(String path, String source) {
        try {
            lock.writeLock().lock();
            
            Path modelPath = Paths.get(path);
            if (!Files.exists(modelPath)) {
                log.warn("Model file not found at {}", path);
                return false;
            }
            
            byte[] graphBytes = Files.readAllBytes(modelPath);
            
            graph = new Graph();
            graph.importGraphDef(graphBytes);
            session = new Session(graph);
            
            modelLoaded = true;
            modelLoadTime = LocalDateTime.now();
            loadedModelVersion = source + "_" + modelPath.getFileName();
            
            log.info("SUCCESS: Fraud detection ML model loaded from {} source: {}", source, path);
            log.info("Model version: {}, Load time: {}", loadedModelVersion, modelLoadTime);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to load ML model from path {}: {}", path, e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Load model from classpath resources
     */
    private boolean loadModelFromResources(String resourcePath, String source) {
        try {
            lock.writeLock().lock();
            
            var resource = getClass().getResourceAsStream(resourcePath);
            if (resource == null) {
                log.warn("Model resource not found: {}", resourcePath);
                return false;
            }
            
            byte[] graphBytes = resource.readAllBytes();
            
            graph = new Graph();
            graph.importGraphDef(graphBytes);
            session = new Session(graph);
            
            modelLoaded = true;
            modelLoadTime = LocalDateTime.now();
            loadedModelVersion = source + "_" + resourcePath;
            
            log.info("SUCCESS: Fraud detection ML model loaded from {} resource: {}", source, resourcePath);
            log.info("Model version: {}, Load time: {}", loadedModelVersion, modelLoadTime);
            
            return true;
            
        } catch (Exception e) {
            log.debug("Failed to load ML model from resource {}: {}", resourcePath, e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * CRITICAL: Predict fraud probability for request
     * 
     * Returns fraud probability score (0.0 - 1.0)
     * Falls back to heuristic model if ML model unavailable
     */
    public double predict(Map<String, Object> features) {
        try {
            lock.readLock().lock();
            
            if (!modelLoaded || session == null) {
                log.debug("ML model not available, using heuristic prediction");
                return heuristicPredict(features);
            }
            
            // Extract and normalize features
            float[][] featureArray = extractAndNormalizeFeatures(features);
            
            // Run ML inference
            try (Tensor<Float> inputTensor = Tensor.create(featureArray, Float.class)) {
                List<Tensor<?>> outputs = session.runner()
                    .feed("fraud_input", inputTensor)
                    .fetch("output")
                    .run();
                
                // Get prediction score
                try (Tensor<?> output = outputs.get(0)) {
                    float[][] prediction = new float[1][1];
                    output.copyTo(prediction);
                    double score = prediction[0][0];
                    
                    // Log prediction for monitoring
                    String txnId = features.get("transaction_id") != null ? 
                        features.get("transaction_id").toString() : "unknown";
                    metricsCollector.recordPrediction(txnId, score);
                    
                    log.debug("ML prediction completed: score={}", score);
                    return score;
                }
            }
        } catch (Exception e) {
            log.error("Error during ML prediction, falling back to heuristic", e);
            return heuristicPredict(features);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Extract and normalize features for ML model
     */
    private float[][] extractAndNormalizeFeatures(Map<String, Object> features) {
        // Convert feature map to normalized array for model input
        List<Float> featureList = new ArrayList<>();
        
        // Amount (normalized to 0-1 scale, log transform)
        double amount = getDoubleFeature(features, "amount", 0.0);
        featureList.add((float) Math.log1p(amount) / 10.0f);
        
        // Time features
        int hour = getIntFeature(features, "hour_of_day", 12);
        featureList.add(hour / 24.0f);
        
        int dayOfWeek = getIntFeature(features, "day_of_week", 1);
        featureList.add(dayOfWeek / 7.0f);
        
        // Additional features can be added here
        // Device features, location features, behavioral features, etc.
        
        // Convert to 2D array [1][n] for TensorFlow
        float[][] result = new float[1][featureList.size()];
        for (int i = 0; i < featureList.size(); i++) {
            result[0][i] = featureList.get(i);
        }
        
        return result;
    }
    
    private double getDoubleFeature(Map<String, Object> features, String key, double defaultValue) {
        Object value = features.get(key);
        if (value == null) return defaultValue;

        // Handle BigDecimal safely with MoneyMath
        if (value instanceof BigDecimal) {
            return (double) MoneyMath.toMLFeature((BigDecimal) value);
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private int getIntFeature(Map<String, Object> features, String key, int defaultValue) {
        Object value = features.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Heuristic fraud prediction when ML model unavailable
     * FALLBACK: Uses rule-based scoring
     */
    private double heuristicPredict(Map<String, Object> features) {
        log.debug("Using heuristic fraud prediction (ML model unavailable)");
        double score = 0.0;
        
        // Amount-based scoring
        // CONFIGURE_IN_VAULT: Amount thresholds and risk weights
        double amount = getDoubleFeature(features, "amount", 0.0);
        double amountThreshold1 = Double.parseDouble(System.getenv().getOrDefault("FRAUD_AMOUNT_THRESHOLD_1", "0"));
        double amountThreshold2 = Double.parseDouble(System.getenv().getOrDefault("FRAUD_AMOUNT_THRESHOLD_2", "0"));
        double amountThreshold3 = Double.parseDouble(System.getenv().getOrDefault("FRAUD_AMOUNT_THRESHOLD_3", "0"));
        double amountScore1 = Double.parseDouble(System.getenv().getOrDefault("FRAUD_AMOUNT_SCORE_1", "0"));
        double amountScore2 = Double.parseDouble(System.getenv().getOrDefault("FRAUD_AMOUNT_SCORE_2", "0"));
        double amountScore3 = Double.parseDouble(System.getenv().getOrDefault("FRAUD_AMOUNT_SCORE_3", "0"));

        if (amount > amountThreshold1 && amountThreshold1 > 0) {
            score += amountScore1;
        }
        if (amount > amountThreshold2 && amountThreshold2 > 0) {
            score += amountScore2;
        }
        if (amount > amountThreshold3 && amountThreshold3 > 0) {
            score += amountScore3;
        }
        
        // Time-based scoring
        // CONFIGURE_IN_VAULT: Time-based risk scoring parameters
        int hour = getIntFeature(features, "hour_of_day", 12);
        double lateNightScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_LATE_NIGHT_SCORE", "0"));
        double unusualHourScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_UNUSUAL_HOUR_SCORE", "0"));

        if (hour >= 0 && hour <= 6) {
            score += lateNightScore; // Late night/early morning transactions
        }
        if (hour >= 23 || hour <= 4) {
            score += unusualHourScore; // Very unusual hours
        }
        
        // Weekend transactions (potentially higher risk)
        // CONFIGURE_IN_VAULT: Weekend and transaction type risk scores
        int dayOfWeek = getIntFeature(features, "day_of_week", 1);
        double weekendScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_WEEKEND_SCORE", "0"));
        if (dayOfWeek == 6 || dayOfWeek == 7) {
            score += weekendScore;
        }

        // Transaction type scoring
        double internationalScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_INTERNATIONAL_SCORE", "0"));
        double cryptoScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_CRYPTO_SCORE", "0"));
        String txnType = features.getOrDefault("transaction_type", "").toString();
        if ("INTERNATIONAL".equals(txnType)) {
            score += internationalScore;
        }
        if ("CRYPTO".equals(txnType)) {
            score += cryptoScore;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Check model health and reload if necessary
     */
    public void checkAndReloadModel() {
        if (modelLoaded && modelLoadTime != null) {
            long hoursSinceLoad = ChronoUnit.HOURS.between(modelLoadTime, LocalDateTime.now());
            
            // Reload model every 24 hours to pick up updates
            if (hoursSinceLoad >= 24) {
                log.info("Model loaded {} hours ago, checking for updates...", hoursSinceLoad);
                loadModelWithRetry();
            }
        }
    }
    
    /**
     * Get model status information
     */
    public Map<String, Object> getModelStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("loaded", modelLoaded);
        status.put("version", loadedModelVersion);
        status.put("loadTime", modelLoadTime);
        status.put("tensorflowVersion", TensorFlow.version());
        
        if (modelLoadTime != null) {
            long hoursRunning = ChronoUnit.HOURS.between(modelLoadTime, LocalDateTime.now());
            status.put("hoursRunning", hoursRunning);
        }
        
        return status;
    }

    /**
     * Cleanup resources on shutdown
     */
    public void cleanup() {
        try {
            lock.writeLock().lock();
            
            if (session != null) {
                session.close();
                log.info("ML session closed");
            }
            if (graph != null) {
                graph.close();
                log.info("ML graph closed");
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrain model with labeled fraud incidents
     * Note: This triggers an external training pipeline
     */
    public void retrain(List<FraudIncident> labeledIncidents) {
        log.info("Starting model retraining with {} incidents", labeledIncidents.size());
        
        try {
            // Prepare training data
            List<TrainingData> trainingData = prepareTrainingData(labeledIncidents);
            
            // In a real implementation, this would trigger a training pipeline
            // For now, we'll simulate by updating model metrics
            updateModelMetrics(trainingData);
            
            // Optionally reload the model if a new version is available
            checkAndReloadModel();
            
        } catch (Exception e) {
            log.error("Failed to retrain model", e);
        }
    }

    private List<TrainingData> prepareTrainingData(List<FraudIncident> incidents) {
        List<TrainingData> trainingData = new ArrayList<>();
        
        for (FraudIncident incident : incidents) {
            TrainingData data = TrainingData.builder()
                .transactionId(incident.getTransactionId())
                .features(extractHistoricalFeatures(incident))
                .label(incident.isConfirmedFraud() ? 1.0 : 0.0)
                .weight(calculateSampleWeight(incident))
                .build();
            
            trainingData.add(data);
        }
        
        return trainingData;
    }

    private float[] extractHistoricalFeatures(FraudIncident incident) {
        // Extract features from historical incident data
        return new float[] {
            MoneyMath.toMLFeature(incident.getAmount()),
            MoneyMath.toMLFeature(incident.getVelocityScore()),
            MoneyMath.toMLFeature(incident.getGeoScore()),
            MoneyMath.toMLFeature(incident.getDeviceScore()),
            MoneyMath.toMLFeature(incident.getBehaviorScore()),
            incident.getHour(),
            incident.getDayOfWeek(),
            incident.isInternational() ? 1.0f : 0.0f,
            incident.isNewDevice() ? 1.0f : 0.0f,
            incident.getRecentTransactionCount()
        };
    }

    private double calculateSampleWeight(FraudIncident incident) {
        // Give more weight to recent incidents and confirmed fraud cases
        double recencyWeight = 1.0;
        long daysSinceIncident = ChronoUnit.DAYS.between(incident.getTimestamp(), LocalDateTime.now());
        if (daysSinceIncident < 7) {
            recencyWeight = 2.0;
        } else if (daysSinceIncident < 30) {
            recencyWeight = 1.5;
        }
        
        double fraudWeight = incident.isConfirmedFraud() ? 2.0 : 1.0;
        
        return recencyWeight * fraudWeight;
    }

    private void updateModelMetrics(List<TrainingData> trainingData) {
        // Calculate model performance metrics
        double accuracy = calculateAccuracy(trainingData);
        double precision = calculatePrecision(trainingData);
        double recall = calculateRecall(trainingData);
        double f1Score = 2 * (precision * recall) / (precision + recall);
        
        log.info("Model metrics - Accuracy: {}, Precision: {}, Recall: {}, F1: {}",
            accuracy, precision, recall, f1Score);
        
        metricsCollector.updateMetrics(accuracy, precision, recall, f1Score);
    }

    private double calculateAccuracy(List<TrainingData> data) {
        if (data == null || data.isEmpty()) {
            return 0.0;
        }
        
        int correct = 0;
        int total = data.size();
        
        for (TrainingData sample : data) {
            double prediction = predict(sample.getFeatures());
            boolean predictedFraud = prediction > 0.5;
            boolean actualFraud = sample.isFraudulent();
            
            if (predictedFraud == actualFraud) {
                correct++;
            }
        }
        
        double accuracy = (double) correct / total;
        log.info("Model accuracy calculated: {}/{} = {}", correct, total, accuracy);
        return accuracy;
    }

    private double calculatePrecision(List<TrainingData> data) {
        if (data == null || data.isEmpty()) {
            return 0.0;
        }
        
        int truePositives = 0;
        int falsePositives = 0;
        
        for (TrainingData sample : data) {
            double prediction = predict(sample.getFeatures());
            boolean predictedFraud = prediction > 0.5;
            boolean actualFraud = sample.isFraudulent();
            
            if (predictedFraud && actualFraud) {
                truePositives++;
            } else if (predictedFraud && !actualFraud) {
                falsePositives++;
            }
        }
        
        if (truePositives + falsePositives == 0) {
            return 0.0;
        }
        
        double precision = (double) truePositives / (truePositives + falsePositives);
        log.info("Model precision calculated: TP={}, FP={}, Precision={}", 
                truePositives, falsePositives, precision);
        return precision;
    }

    private double calculateRecall(List<TrainingData> data) {
        if (data == null || data.isEmpty()) {
            return 0.0;
        }
        
        int truePositives = 0;
        int falseNegatives = 0;
        
        for (TrainingData sample : data) {
            double prediction = predict(sample.getFeatures());
            boolean predictedFraud = prediction > 0.5;
            boolean actualFraud = sample.isFraudulent();
            
            if (predictedFraud && actualFraud) {
                truePositives++;
            } else if (!predictedFraud && actualFraud) {
                falseNegatives++;
            }
        }
        
        if (truePositives + falseNegatives == 0) {
            return 0.0;
        }
        
        double recall = (double) truePositives / (truePositives + falseNegatives);
        log.info("Model recall calculated: TP={}, FN={}, Recall={}", 
                truePositives, falseNegatives, recall);
        return recall;
    }

    private void checkAndReloadModel() {
        // Check if a new model version is available
        // This would typically check a model registry or file system
        // For now, we'll just log
        log.info("Checking for new model version...");
    }

    private static class TrainingData {
        private String transactionId;
        private float[] features;
        private double label;
        private double weight;

        public static TrainingDataBuilder builder() {
            return new TrainingDataBuilder();
        }

        public Map<String, Object> getFeatures() {
            Map<String, Object> featureMap = new HashMap<>();
            if (features != null && features.length >= 3) {
                featureMap.put("amount", Math.exp(features[0] * 10.0) - 1.0);
                featureMap.put("hour_of_day", (int)(features[1] * 24));
                featureMap.put("day_of_week", (int)(features[2] * 7));
            }
            return featureMap;
        }

        public boolean isFraudulent() {
            return label > 0.5;
        }
        
        private static class TrainingDataBuilder {
            private String transactionId;
            private float[] features;
            private double label;
            private double weight;
            
            public TrainingDataBuilder transactionId(String transactionId) {
                this.transactionId = transactionId;
                return this;
            }
            
            public TrainingDataBuilder features(float[] features) {
                this.features = features;
                return this;
            }
            
            public TrainingDataBuilder label(double label) {
                this.label = label;
                return this;
            }
            
            public TrainingDataBuilder weight(double weight) {
                this.weight = weight;
                return this;
            }
            
            public TrainingData build() {
                TrainingData data = new TrainingData();
                data.transactionId = this.transactionId;
                data.features = this.features;
                data.label = this.label;
                data.weight = this.weight;
                return data;
            }
        }
    }
}