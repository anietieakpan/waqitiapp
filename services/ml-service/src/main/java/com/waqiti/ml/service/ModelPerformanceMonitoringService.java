package com.waqiti.ml.service;

import com.waqiti.ml.entity.ModelPerformanceMetrics;
import com.waqiti.ml.repository.ModelPerformanceMetricsRepository;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Production-ready Model Performance Monitoring Service for ML models.
 * Provides comprehensive monitoring, drift detection, and alerting capabilities.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelPerformanceMonitoringService {

    private final ModelPerformanceMetricsRepository metricsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${ml.monitoring.drift.threshold:0.05}")
    private double driftThreshold;

    @Value("${ml.monitoring.accuracy.threshold:0.80}")
    private double accuracyThreshold;

    @Value("${ml.monitoring.error.rate.threshold:0.10}")
    private double errorRateThreshold;

    @Value("${ml.monitoring.latency.threshold.ms:1000}")
    private long latencyThresholdMs;

    @Value("${ml.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${ml.monitoring.alert.cooldown.hours:1}")
    private int alertCooldownHours;

    private static final String METRICS_CACHE_PREFIX = "ml:metrics:";
    private static final String ALERT_CACHE_PREFIX = "ml:alert:";
    private static final String DRIFT_CACHE_PREFIX = "ml:drift:";

    // Real-time metrics tracking
    private final Map<String, RealTimeMetrics> realTimeMetrics = new ConcurrentHashMap<>();
    
    // Drift detection state
    private final Map<String, DriftDetectionState> driftStates = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        if (monitoringEnabled) {
            log.info("Model Performance Monitoring Service initialized");
            log.info("Drift threshold: {}, Accuracy threshold: {}", driftThreshold, accuracyThreshold);
        } else {
            log.info("Model Performance Monitoring Service disabled");
        }
    }

    /**
     * Record a prediction event for monitoring
     */
    @Traced(operation = "record_prediction")
    public void recordPrediction(String modelName, String modelVersion, 
                                PredictionEvent event) {
        if (!monitoringEnabled) return;
        
        try {
            log.debug("Recording prediction event for model: {} v{}", modelName, modelVersion);
            
            // Update real-time metrics
            updateRealTimeMetrics(modelName, modelVersion, event);
            
            // Check for immediate anomalies
            checkForAnomalies(modelName, modelVersion, event);
            
            // Update drift detection
            updateDriftDetection(modelName, modelVersion, event);
            
            // Persist metrics if needed (batch or threshold-based)
            if (shouldPersistMetrics(modelName, modelVersion)) {
                persistMetrics(modelName, modelVersion);
            }
            
        } catch (Exception e) {
            log.error("Error recording prediction for model {} v{}: {}", 
                modelName, modelVersion, e.getMessage());
        }
    }

    /**
     * Get current performance metrics for a model
     */
    @Traced(operation = "get_performance_metrics")
    public ModelPerformanceMetrics getCurrentMetrics(String modelName, String modelVersion) {
        try {
            String cacheKey = METRICS_CACHE_PREFIX + modelName + ":" + modelVersion;
            
            // Check cache first
            ModelPerformanceMetrics cached = (ModelPerformanceMetrics) 
                redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
            
            // Get from database
            Optional<ModelPerformanceMetrics> metrics = metricsRepository
                .findTopByModelNameAndModelVersionOrderByCreatedAtDesc(modelName, modelVersion);
            
            if (metrics.isPresent()) {
                // Cache for 5 minutes
                redisTemplate.opsForValue().set(cacheKey, metrics.get(), 5, TimeUnit.MINUTES);
                return metrics.get();
            }
            
            // Create initial metrics if none exist
            return createInitialMetrics(modelName, modelVersion);
            
        } catch (Exception e) {
            log.error("Error getting metrics for model {} v{}: {}", 
                modelName, modelVersion, e.getMessage());
            return createInitialMetrics(modelName, modelVersion);
        }
    }

    /**
     * Calculate and update performance metrics
     */
    @Transactional
    public ModelPerformanceMetrics updatePerformanceMetrics(String modelName, String modelVersion,
                                                           List<PredictionResult> predictions,
                                                           List<GroundTruth> groundTruths) {
        try {
            log.info("Updating performance metrics for model: {} v{}", modelName, modelVersion);
            
            // Calculate metrics
            PerformanceCalculation calc = calculatePerformanceMetrics(predictions, groundTruths);
            
            // Get or create metrics entity
            ModelPerformanceMetrics metrics = metricsRepository
                .findTopByModelNameAndModelVersionOrderByCreatedAtDesc(modelName, modelVersion)
                .orElse(ModelPerformanceMetrics.builder()
                    .modelName(modelName)
                    .modelVersion(modelVersion)
                    .predictionCount(0L)
                    .errorCount(0L)
                    .build());
            
            // Update metrics
            metrics.setAccuracy(calc.accuracy);
            metrics.setPrecision(calc.precision);
            metrics.setRecall(calc.recall);
            metrics.setF1Score(calc.f1Score);
            metrics.setAucRoc(calc.aucRoc);
            metrics.setPredictionCount(metrics.getPredictionCount() + predictions.size());
            metrics.setAverageInferenceTimeMs(calc.avgInferenceTime);
            
            // Calculate drift score
            double driftScore = calculateDriftScore(modelName, modelVersion, predictions);
            metrics.setDriftScore(driftScore);
            
            // Save metrics
            metrics = metricsRepository.save(metrics);
            
            // Check for performance degradation
            checkPerformanceDegradation(metrics);
            
            // Check for drift
            if (driftScore > driftThreshold) {
                handleDriftDetected(modelName, modelVersion, driftScore);
            }
            
            // Cache updated metrics
            String cacheKey = METRICS_CACHE_PREFIX + modelName + ":" + modelVersion;
            redisTemplate.opsForValue().set(cacheKey, metrics, 5, TimeUnit.MINUTES);
            
            log.info("Updated metrics for model {} v{}: Accuracy={}, Drift={}", 
                modelName, modelVersion, metrics.getAccuracy(), driftScore);
            
            return metrics;
            
        } catch (Exception e) {
            log.error("Error updating performance metrics: {}", e.getMessage());
            throw new MLProcessingException("Failed to update performance metrics", e);
        }
    }

    /**
     * Get model health status
     */
    public ModelHealthStatus getModelHealthStatus(String modelName, String modelVersion) {
        try {
            ModelPerformanceMetrics metrics = getCurrentMetrics(modelName, modelVersion);
            RealTimeMetrics realTime = realTimeMetrics.get(modelName + ":" + modelVersion);
            
            return ModelHealthStatus.builder()
                .modelName(modelName)
                .modelVersion(modelVersion)
                .isHealthy(metrics.isHealthy())
                .accuracy(metrics.getAccuracy())
                .errorRate(metrics.getErrorRate())
                .driftScore(metrics.getDriftScore())
                .avgInferenceTimeMs(metrics.getAverageInferenceTimeMs())
                .predictionCount(metrics.getPredictionCount())
                .lastUpdated(metrics.getUpdatedAt())
                .currentThroughput(realTime != null ? realTime.getCurrentThroughput() : 0.0)
                .healthScore(calculateHealthScore(metrics))
                .alerts(getActiveAlerts(modelName, modelVersion))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting health status for model {} v{}: {}", 
                modelName, modelVersion, e.getMessage());
            
            return ModelHealthStatus.builder()
                .modelName(modelName)
                .modelVersion(modelVersion)
                .isHealthy(false)
                .healthScore(0.0)
                .alerts(List.of("Failed to retrieve health status"))
                .build();
        }
    }

    /**
     * Scheduled task to persist metrics
     */
    @Scheduled(fixedDelayString = "${ml.monitoring.persist.interval.ms:300000}") // 5 minutes
    public void persistRealTimeMetrics() {
        if (!monitoringEnabled) return;
        
        try {
            log.debug("Persisting real-time metrics for {} models", realTimeMetrics.size());
            
            for (Map.Entry<String, RealTimeMetrics> entry : realTimeMetrics.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    persistMetrics(parts[0], parts[1]);
                }
            }
            
        } catch (Exception e) {
            log.error("Error persisting real-time metrics: {}", e.getMessage());
        }
    }

    /**
     * Scheduled task to check for model drift
     */
    @Scheduled(fixedDelayString = "${ml.monitoring.drift.check.ms:900000}") // 15 minutes
    public void checkForModelDrift() {
        if (!monitoringEnabled) return;
        
        try {
            log.debug("Checking for model drift across {} models", driftStates.size());
            
            for (Map.Entry<String, DriftDetectionState> entry : driftStates.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    checkModelDrift(parts[0], parts[1], entry.getValue());
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking for model drift: {}", e.getMessage());
        }
    }

    /**
     * Get performance trends
     */
    public List<PerformanceTrend> getPerformanceTrends(String modelName, String modelVersion, 
                                                      LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<ModelPerformanceMetrics> metrics = metricsRepository
                .findByModelNameAndModelVersionAndCreatedAtBetweenOrderByCreatedAt(
                    modelName, modelVersion, startDate, endDate);
            
            return metrics.stream()
                .map(m -> PerformanceTrend.builder()
                    .timestamp(m.getCreatedAt())
                    .accuracy(m.getAccuracy())
                    .precision(m.getPrecision())
                    .recall(m.getRecall())
                    .f1Score(m.getF1Score())
                    .driftScore(m.getDriftScore())
                    .errorRate(m.getErrorRate())
                    .throughput(m.getPredictionCount().doubleValue() / 
                        Math.max(1, ChronoUnit.HOURS.between(m.getCreatedAt(), m.getUpdatedAt())))
                    .build())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting performance trends: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // Private helper methods

    private void updateRealTimeMetrics(String modelName, String modelVersion, PredictionEvent event) {
        String key = modelName + ":" + modelVersion;
        RealTimeMetrics metrics = realTimeMetrics.computeIfAbsent(key, 
            k -> new RealTimeMetrics(modelName, modelVersion));
        
        metrics.recordPrediction(event);
    }

    private void checkForAnomalies(String modelName, String modelVersion, PredictionEvent event) {
        // Check for unusual inference times
        if (event.getInferenceTimeMs() > latencyThresholdMs) {
            sendAlert(modelName, modelVersion, "HIGH_LATENCY", 
                "Inference time " + event.getInferenceTimeMs() + "ms exceeds threshold");
        }
        
        // Check for unusual confidence scores
        if (event.getConfidenceScore() != null && event.getConfidenceScore() < 0.5) {
            log.warn("Low confidence prediction: {} for model {} v{}", 
                event.getConfidenceScore(), modelName, modelVersion);
        }
    }

    private void updateDriftDetection(String modelName, String modelVersion, PredictionEvent event) {
        String key = modelName + ":" + modelVersion;
        DriftDetectionState state = driftStates.computeIfAbsent(key, 
            k -> new DriftDetectionState());
        
        state.addDataPoint(event);
    }

    private boolean shouldPersistMetrics(String modelName, String modelVersion) {
        String key = modelName + ":" + modelVersion;
        RealTimeMetrics metrics = realTimeMetrics.get(key);
        
        if (metrics == null) return false;
        
        // Persist every 1000 predictions or every 5 minutes
        return metrics.getPredictionCount() % 1000 == 0 || 
               ChronoUnit.MINUTES.between(metrics.getLastPersisted(), LocalDateTime.now()) >= 5;
    }

    private void persistMetrics(String modelName, String modelVersion) {
        try {
            String key = modelName + ":" + modelVersion;
            RealTimeMetrics realTime = realTimeMetrics.get(key);
            
            if (realTime == null) return;
            
            ModelPerformanceMetrics metrics = metricsRepository
                .findTopByModelNameAndModelVersionOrderByCreatedAtDesc(modelName, modelVersion)
                .orElse(ModelPerformanceMetrics.builder()
                    .modelName(modelName)
                    .modelVersion(modelVersion)
                    .predictionCount(0L)
                    .errorCount(0L)
                    .build());
            
            metrics.setPredictionCount(metrics.getPredictionCount() + realTime.getPredictionCount());
            metrics.setErrorCount(metrics.getErrorCount() + realTime.getErrorCount());
            metrics.setAverageInferenceTimeMs(realTime.getAverageInferenceTime());
            
            metricsRepository.save(metrics);
            
            realTime.markPersisted();
            
        } catch (Exception e) {
            log.error("Error persisting metrics: {}", e.getMessage());
        }
    }

    private ModelPerformanceMetrics createInitialMetrics(String modelName, String modelVersion) {
        return ModelPerformanceMetrics.builder()
            .modelName(modelName)
            .modelVersion(modelVersion)
            .accuracy(0.0)
            .precision(0.0)
            .recall(0.0)
            .f1Score(0.0)
            .aucRoc(0.0)
            .predictionCount(0L)
            .errorCount(0L)
            .averageInferenceTimeMs(0.0)
            .driftScore(0.0)
            .build();
    }

    private PerformanceCalculation calculatePerformanceMetrics(List<PredictionResult> predictions,
                                                             List<GroundTruth> groundTruths) {
        if (predictions.size() != groundTruths.size()) {
            throw new IllegalArgumentException("Predictions and ground truths must have same size");
        }
        
        int tp = 0, tn = 0, fp = 0, fn = 0;
        double totalInferenceTime = 0.0;
        
        for (int i = 0; i < predictions.size(); i++) {
            PredictionResult pred = predictions.get(i);
            GroundTruth truth = groundTruths.get(i);
            
            boolean predicted = pred.getPredictedClass() == 1;
            boolean actual = truth.getActualClass() == 1;
            
            if (predicted && actual) tp++;
            else if (!predicted && !actual) tn++;
            else if (predicted && !actual) fp++;
            else fn++;
            
            totalInferenceTime += pred.getInferenceTimeMs();
        }
        
        double accuracy = (double) (tp + tn) / (tp + tn + fp + fn);
        double precision = tp + fp > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = tp + fn > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1 = precision + recall > 0 ? 2 * (precision * recall) / (precision + recall) : 0.0;
        
        // Simplified AUC calculation
        double auc = calculateAUC(predictions, groundTruths);
        
        return PerformanceCalculation.builder()
            .accuracy(accuracy)
            .precision(precision)
            .recall(recall)
            .f1Score(f1)
            .aucRoc(auc)
            .avgInferenceTime(totalInferenceTime / predictions.size())
            .build();
    }

    private double calculateAUC(List<PredictionResult> predictions, List<GroundTruth> groundTruths) {
        // Simplified AUC calculation - in production would use proper ROC curve calculation
        List<PredictionTruthPair> pairs = new ArrayList<>();
        for (int i = 0; i < predictions.size(); i++) {
            pairs.add(new PredictionTruthPair(
                predictions.get(i).getConfidenceScore(),
                groundTruths.get(i).getActualClass() == 1
            ));
        }
        
        // Sort by confidence score descending
        pairs.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        
        int positives = (int) pairs.stream().mapToInt(p -> p.isPositive ? 1 : 0).sum();
        int negatives = pairs.size() - positives;
        
        if (positives == 0 || negatives == 0) return 0.5;
        
        double auc = 0.0;
        int truePositives = 0;
        int falsePositives = 0;
        
        for (PredictionTruthPair pair : pairs) {
            if (pair.isPositive) {
                truePositives++;
            } else {
                falsePositives++;
                auc += (double) truePositives / positives;
            }
        }
        
        return auc / negatives;
    }

    private double calculateDriftScore(String modelName, String modelVersion, 
                                     List<PredictionResult> predictions) {
        // Simplified drift detection using prediction distribution
        String key = modelName + ":" + modelVersion;
        DriftDetectionState state = driftStates.get(key);
        
        if (state == null || state.getBaselineDistribution().isEmpty()) {
            // No baseline, calculate and store
            state = new DriftDetectionState();
            state.updateBaseline(predictions);
            driftStates.put(key, state);
            return 0.0;
        }
        
        // Calculate KL divergence (simplified)
        Map<String, Double> currentDist = calculatePredictionDistribution(predictions);
        return calculateKLDivergence(state.getBaselineDistribution(), currentDist);
    }

    private Map<String, Double> calculatePredictionDistribution(List<PredictionResult> predictions) {
        Map<String, Integer> counts = new HashMap<>();
        
        for (PredictionResult pred : predictions) {
            // Bucket confidence scores
            String bucket = String.valueOf((int) (pred.getConfidenceScore() * 10) / 10.0);
            counts.merge(bucket, 1, Integer::sum);
        }
        
        Map<String, Double> distribution = new HashMap<>();
        int total = predictions.size();
        
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            distribution.put(entry.getKey(), (double) entry.getValue() / total);
        }
        
        return distribution;
    }

    private double calculateKLDivergence(Map<String, Double> p, Map<String, Double> q) {
        double kl = 0.0;
        
        for (String key : p.keySet()) {
            double pVal = p.get(key);
            double qVal = q.getOrDefault(key, 1e-10); // Small epsilon to avoid log(0)
            
            if (pVal > 0) {
                kl += pVal * Math.log(pVal / qVal);
            }
        }
        
        return kl;
    }

    private void checkPerformanceDegradation(ModelPerformanceMetrics metrics) {
        List<String> issues = new ArrayList<>();
        
        if (metrics.getAccuracy() != null && metrics.getAccuracy() < accuracyThreshold) {
            issues.add("Accuracy below threshold: " + metrics.getAccuracy());
        }
        
        if (metrics.getErrorRate() > errorRateThreshold) {
            issues.add("Error rate above threshold: " + metrics.getErrorRate());
        }
        
        if (metrics.getAverageInferenceTimeMs() != null && 
            metrics.getAverageInferenceTimeMs() > latencyThresholdMs) {
            issues.add("Average latency above threshold: " + metrics.getAverageInferenceTimeMs() + "ms");
        }
        
        if (!issues.isEmpty()) {
            sendAlert(metrics.getModelName(), metrics.getModelVersion(), 
                "PERFORMANCE_DEGRADATION", String.join("; ", issues));
        }
    }

    private void handleDriftDetected(String modelName, String modelVersion, double driftScore) {
        log.warn("Model drift detected for {} v{}: drift score = {}", 
            modelName, modelVersion, driftScore);
        
        sendAlert(modelName, modelVersion, "MODEL_DRIFT", 
            "Drift score " + driftScore + " exceeds threshold " + driftThreshold);
        
        // Could trigger model retraining here
        publishModelDriftEvent(modelName, modelVersion, driftScore);
    }

    private void checkModelDrift(String modelName, String modelVersion, DriftDetectionState state) {
        if (state.shouldCheckDrift()) {
            double driftScore = state.calculateCurrentDrift();
            if (driftScore > driftThreshold) {
                handleDriftDetected(modelName, modelVersion, driftScore);
            }
            state.resetDriftCheck();
        }
    }

    private double calculateHealthScore(ModelPerformanceMetrics metrics) {
        double score = 0.0;
        
        // Accuracy component (40%)
        if (metrics.getAccuracy() != null) {
            score += metrics.getAccuracy() * 0.4;
        }
        
        // Error rate component (30%)
        score += (1.0 - Math.min(1.0, metrics.getErrorRate())) * 0.3;
        
        // Drift component (20%)
        if (metrics.getDriftScore() != null) {
            score += (1.0 - Math.min(1.0, metrics.getDriftScore() / driftThreshold)) * 0.2;
        }
        
        // Latency component (10%)
        if (metrics.getAverageInferenceTimeMs() != null) {
            score += (1.0 - Math.min(1.0, metrics.getAverageInferenceTimeMs() / latencyThresholdMs)) * 0.1;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    private List<String> getActiveAlerts(String modelName, String modelVersion) {
        try {
            String alertKey = ALERT_CACHE_PREFIX + modelName + ":" + modelVersion;
            Set<Object> alerts = redisTemplate.opsForSet().members(alertKey);
            
            return alerts != null ? 
                alerts.stream().map(Object::toString).collect(Collectors.toList()) :
                new ArrayList<>();
                
        } catch (Exception e) {
            log.error("Error getting active alerts: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void sendAlert(String modelName, String modelVersion, String alertType, String message) {
        try {
            String alertKey = ALERT_CACHE_PREFIX + modelName + ":" + modelVersion + ":" + alertType;
            
            // Check cooldown
            if (redisTemplate.hasKey(alertKey)) {
                log.debug("Alert {} is in cooldown period", alertType);
                return;
            }
            
            // Set cooldown
            redisTemplate.opsForValue().set(alertKey, message, alertCooldownHours, TimeUnit.HOURS);
            
            // Add to active alerts set
            String activeAlertsKey = ALERT_CACHE_PREFIX + modelName + ":" + modelVersion;
            redisTemplate.opsForSet().add(activeAlertsKey, alertType + ": " + message);
            redisTemplate.expire(activeAlertsKey, alertCooldownHours, TimeUnit.HOURS);
            
            // Send alert event
            Map<String, Object> alertEvent = Map.of(
                "model_name", modelName,
                "model_version", modelVersion,
                "alert_type", alertType,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("ml-model-alerts", modelName, alertEvent);
            
            log.warn("ML Model Alert [{}]: {} v{} - {}", alertType, modelName, modelVersion, message);
            
        } catch (Exception e) {
            log.error("Error sending alert: {}", e.getMessage());
        }
    }

    private void publishModelDriftEvent(String modelName, String modelVersion, double driftScore) {
        try {
            Map<String, Object> event = Map.of(
                "event_type", "MODEL_DRIFT_DETECTED",
                "model_name", modelName,
                "model_version", modelVersion,
                "drift_score", driftScore,
                "threshold", driftThreshold,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("ml-model-events", modelName, event);
            
        } catch (Exception e) {
            log.error("Error publishing drift event: {}", e.getMessage());
        }
    }

    // Inner classes and DTOs

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionEvent {
        private String predictionId;
        private String modelName;
        private String modelVersion;
        private Map<String, Object> inputFeatures;
        private Object prediction;
        private Double confidenceScore;
        private Long inferenceTimeMs;
        private LocalDateTime timestamp;
        private boolean success;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionResult {
        private String predictionId;
        private Integer predictedClass;
        private Double confidenceScore;
        private Long inferenceTimeMs;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroundTruth {
        private String entityId;
        private Integer actualClass;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelHealthStatus {
        private String modelName;
        private String modelVersion;
        private boolean isHealthy;
        private Double accuracy;
        private Double errorRate;
        private Double driftScore;
        private Double avgInferenceTimeMs;
        private Long predictionCount;
        private LocalDateTime lastUpdated;
        private Double currentThroughput;
        private Double healthScore;
        private List<String> alerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceTrend {
        private LocalDateTime timestamp;
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Double driftScore;
        private Double errorRate;
        private Double throughput;
    }

    @Data
    @Builder
    private static class PerformanceCalculation {
        private double accuracy;
        private double precision;
        private double recall;
        private double f1Score;
        private double aucRoc;
        private double avgInferenceTime;
    }

    private static class PredictionTruthPair {
        double confidence;
        boolean isPositive;
        
        PredictionTruthPair(double confidence, boolean isPositive) {
            this.confidence = confidence;
            this.isPositive = isPositive;
        }
    }

    private static class RealTimeMetrics {
        private final String modelName;
        private final String modelVersion;
        private long predictionCount = 0;
        private long errorCount = 0;
        private double totalInferenceTime = 0.0;
        private LocalDateTime lastUpdated = LocalDateTime.now();
        private LocalDateTime lastPersisted = LocalDateTime.now();
        
        public RealTimeMetrics(String modelName, String modelVersion) {
            this.modelName = modelName;
            this.modelVersion = modelVersion;
        }
        
        public synchronized void recordPrediction(PredictionEvent event) {
            predictionCount++;
            if (!event.isSuccess()) {
                errorCount++;
            }
            if (event.getInferenceTimeMs() != null) {
                totalInferenceTime += event.getInferenceTimeMs();
            }
            lastUpdated = LocalDateTime.now();
        }
        
        public long getPredictionCount() { return predictionCount; }
        public long getErrorCount() { return errorCount; }
        public double getAverageInferenceTime() { 
            return predictionCount > 0 ? totalInferenceTime / predictionCount : 0.0; 
        }
        public LocalDateTime getLastPersisted() { return lastPersisted; }
        public void markPersisted() { 
            predictionCount = 0;
            errorCount = 0;
            totalInferenceTime = 0.0;
            lastPersisted = LocalDateTime.now(); 
        }
        public double getCurrentThroughput() {
            long minutesSinceUpdate = ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now());
            return minutesSinceUpdate > 0 ? (double) predictionCount / minutesSinceUpdate : 0.0;
        }
    }

    private static class DriftDetectionState {
        private Map<String, Double> baselineDistribution = new HashMap<>();
        private List<PredictionResult> recentPredictions = new ArrayList<>();
        private LocalDateTime lastDriftCheck = LocalDateTime.now();
        private static final int DRIFT_CHECK_INTERVAL_HOURS = 1;
        private static final int MAX_RECENT_PREDICTIONS = 1000;
        
        public void addDataPoint(PredictionEvent event) {
            if (event.getConfidenceScore() != null) {
                PredictionResult result = PredictionResult.builder()
                    .confidenceScore(event.getConfidenceScore())
                    .inferenceTimeMs(event.getInferenceTimeMs())
                    .timestamp(event.getTimestamp())
                    .build();
                
                recentPredictions.add(result);
                
                // Keep only recent predictions
                if (recentPredictions.size() > MAX_RECENT_PREDICTIONS) {
                    recentPredictions.remove(0);
                }
            }
        }
        
        public void updateBaseline(List<PredictionResult> predictions) {
            baselineDistribution = calculatePredictionDistribution(predictions);
        }
        
        public boolean shouldCheckDrift() {
            return ChronoUnit.HOURS.between(lastDriftCheck, LocalDateTime.now()) >= DRIFT_CHECK_INTERVAL_HOURS
                && recentPredictions.size() >= 100;
        }
        
        public double calculateCurrentDrift() {
            if (baselineDistribution.isEmpty() || recentPredictions.isEmpty()) {
                return 0.0;
            }
            
            Map<String, Double> currentDist = calculatePredictionDistribution(recentPredictions);
            return calculateKLDivergence(baselineDistribution, currentDist);
        }
        
        public void resetDriftCheck() {
            lastDriftCheck = LocalDateTime.now();
        }
        
        public Map<String, Double> getBaselineDistribution() {
            return baselineDistribution;
        }
        
        private Map<String, Double> calculatePredictionDistribution(List<PredictionResult> predictions) {
            Map<String, Integer> counts = new HashMap<>();
            
            for (PredictionResult pred : predictions) {
                String bucket = String.valueOf((int) (pred.getConfidenceScore() * 10) / 10.0);
                counts.merge(bucket, 1, Integer::sum);
            }
            
            Map<String, Double> distribution = new HashMap<>();
            int total = predictions.size();
            
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                distribution.put(entry.getKey(), (double) entry.getValue() / total);
            }
            
            return distribution;
        }
        
        private double calculateKLDivergence(Map<String, Double> p, Map<String, Double> q) {
            double kl = 0.0;
            
            for (String key : p.keySet()) {
                double pVal = p.get(key);
                double qVal = q.getOrDefault(key, 1e-10);
                
                if (pVal > 0) {
                    kl += pVal * Math.log(pVal / qVal);
                }
            }
            
            return kl;
        }
    }
}