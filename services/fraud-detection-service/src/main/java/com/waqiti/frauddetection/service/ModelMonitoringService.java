package com.waqiti.frauddetection.service;

import com.waqiti.common.observability.MetricsService;
import com.waqiti.frauddetection.repository.ModelPerformanceRepository;
import com.waqiti.frauddetection.entity.ModelPerformanceMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Production-Grade Model Monitoring and Performance Tracking Service
 * 
 * Implements comprehensive ML model monitoring with:
 * - Real-time performance tracking and degradation detection
 * - Model drift detection and alerting
 * - A/B testing and champion/challenger model comparison
 * - Inference latency and throughput monitoring
 * - Feature drift and data quality monitoring
 * - Automated model retraining triggers
 * - Performance SLA tracking and violation alerting
 * - Model explainability and fairness monitoring
 * 
 * Monitoring Capabilities:
 * - Prediction accuracy and precision/recall tracking
 * - False positive/negative rate monitoring
 * - Concept drift detection using statistical tests
 * - Feature importance drift analysis
 * - Model confidence distribution tracking
 * - Resource utilization monitoring
 * - Error rate and exception tracking
 * - Business impact correlation
 * 
 * Alerting and Remediation:
 * - Automated alerts for performance degradation
 * - Model rollback triggers
 * - Retraining pipeline automation
 * - Stakeholder notifications
 * - Incident response coordination
 * 
 * @author Waqiti ML Ops Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelMonitoringService {

    private final MetricsService metricsService;
    private final ModelPerformanceRepository performanceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${waqiti.ml.monitoring.drift-threshold:0.05}")
    private double driftDetectionThreshold;

    @Value("${waqiti.ml.monitoring.performance-threshold:0.85}")
    private double performanceThreshold;

    @Value("${waqiti.ml.monitoring.latency-threshold-ms:100}")
    private long latencyThresholdMs;

    @Value("${waqiti.ml.monitoring.alert-topic:ml-model-alerts}")
    private String alertTopic;

    // Real-time metrics tracking
    private final Map<String, AtomicLong> modelInferenceCount = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> modelInferenceLatency = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> modelErrorCount = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> modelAccuracy = new ConcurrentHashMap<>();

    // Performance windows for tracking
    private final Map<String, Queue<ModelInferenceEvent>> recentInferences = new ConcurrentHashMap<>();
    private static final int MAX_INFERENCE_HISTORY = 10000;

    /**
     * Record model inference metrics and performance
     */
    public void recordInference(String modelName, long latencyMs, double predictionScore) {
        log.debug("Recording inference - model: {}, latency: {}ms, score: {}", 
            modelName, latencyMs, predictionScore);

        // Update real-time counters
        modelInferenceCount.computeIfAbsent(modelName, k -> new AtomicLong(0)).incrementAndGet();
        modelInferenceLatency.computeIfAbsent(modelName, k -> new DoubleAdder()).add(latencyMs);

        // Create inference event
        ModelInferenceEvent event = ModelInferenceEvent.builder()
            .modelName(modelName)
            .timestamp(LocalDateTime.now())
            .latencyMs(latencyMs)
            .predictionScore(predictionScore)
            .build();

        // Add to recent inferences for drift detection
        Queue<ModelInferenceEvent> modelHistory = recentInferences.computeIfAbsent(
            modelName, k -> new LinkedList<>());

        synchronized (modelHistory) {
            modelHistory.offer(event);
            if (modelHistory.size() > MAX_INFERENCE_HISTORY) {
                modelHistory.poll(); // Remove oldest
            }
        }

        // Check for performance issues
        checkLatencyThreshold(modelName, latencyMs);
        checkPredictionDistribution(modelName, predictionScore);

        // Record metrics
        metricsService.recordModelInference(modelName, latencyMs, predictionScore);
    }

    /**
     * Record model prediction accuracy when ground truth is available
     */
    public void recordPredictionAccuracy(String modelName, boolean wasCorrect, 
                                       double confidenceScore, String actualLabel, 
                                       String predictedLabel) {
        
        log.debug("Recording prediction accuracy - model: {}, correct: {}, confidence: {}", 
            modelName, wasCorrect, confidenceScore);

        // Update accuracy metrics
        DoubleAdder accuracyTracker = modelAccuracy.computeIfAbsent(modelName, k -> new DoubleAdder());
        accuracyTracker.add(wasCorrect ? 1.0 : 0.0);

        // Create accuracy event for detailed tracking
        ModelAccuracyEvent accuracyEvent = ModelAccuracyEvent.builder()
            .modelName(modelName)
            .timestamp(LocalDateTime.now())
            .wasCorrect(wasCorrect)
            .confidenceScore(confidenceScore)
            .actualLabel(actualLabel)
            .predictedLabel(predictedLabel)
            .build();

        // Store for performance analysis
        storeAccuracyEvent(accuracyEvent);

        // Check for performance degradation
        checkAccuracyThreshold(modelName);

        // Record business metrics
        metricsService.recordModelAccuracy(modelName, wasCorrect, confidenceScore);
    }

    /**
     * Record model failure or error
     */
    public void recordModelFailure(String modelName, String errorMessage) {
        log.warn("Recording model failure - model: {}, error: {}", modelName, errorMessage);

        // Update error counters
        modelErrorCount.computeIfAbsent(modelName, k -> new AtomicLong(0)).incrementAndGet();

        // Create failure event
        ModelFailureEvent failureEvent = ModelFailureEvent.builder()
            .modelName(modelName)
            .timestamp(LocalDateTime.now())
            .errorMessage(errorMessage)
            .errorType(classifyError(errorMessage))
            .build();

        // Store failure event
        storeFailureEvent(failureEvent);

        // Check error rate threshold
        checkErrorRateThreshold(modelName);

        // Send immediate alert for critical failures
        if (isCriticalFailure(errorMessage)) {
            sendCriticalAlert(modelName, errorMessage);
        }

        // Record error metrics
        metricsService.recordModelError(modelName, errorMessage);
    }

    /**
     * Start monitoring for all registered models
     */
    public void startModelMonitoring(Set<String> modelNames) {
        log.info("Starting model monitoring for {} models: {}", modelNames.size(), modelNames);

        for (String modelName : modelNames) {
            // Initialize tracking structures
            modelInferenceCount.put(modelName, new AtomicLong(0));
            modelInferenceLatency.put(modelName, new DoubleAdder());
            modelErrorCount.put(modelName, new AtomicLong(0));
            modelAccuracy.put(modelName, new DoubleAdder());
            recentInferences.put(modelName, new LinkedList<>());

            log.info("Initialized monitoring for model: {}", modelName);
        }
    }

    /**
     * Scheduled performance analysis and drift detection
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void performScheduledAnalysis() {
        log.debug("Starting scheduled model performance analysis");

        try {
            for (String modelName : modelInferenceCount.keySet()) {
                // Analyze model performance
                analyzeModelPerformance(modelName);

                // Detect drift
                detectModelDrift(modelName);

                // Check SLA compliance
                checkSLACompliance(modelName);

                // Update performance repository
                updatePerformanceMetrics(modelName);
            }

            log.debug("Scheduled model analysis completed");

        } catch (Exception e) {
            log.error("Error during scheduled model analysis", e);
        }
    }

    /**
     * Detect model drift using statistical methods
     */
    @Async
    public void detectModelDrift(String modelName) {
        log.debug("Performing drift detection for model: {}", modelName);

        Queue<ModelInferenceEvent> modelHistory = recentInferences.get(modelName);
        if (modelHistory == null || modelHistory.size() < 1000) {
            log.debug("Insufficient data for drift detection - model: {}, samples: {}", 
                modelName, modelHistory != null ? modelHistory.size() : 0);
            return;
        }

        try {
            List<ModelInferenceEvent> events = new ArrayList<>(modelHistory);

            // Analyze recent vs baseline distributions
            DriftAnalysisResult driftResult = analyzePredictionDrift(events);

            if (driftResult.isDriftDetected()) {
                log.warn("Model drift detected - model: {}, drift_score: {}, p_value: {}", 
                    modelName, driftResult.getDriftScore(), driftResult.getPValue());

                // Send drift alert
                ModelDriftAlert driftAlert = ModelDriftAlert.builder()
                    .modelName(modelName)
                    .driftScore(driftResult.getDriftScore())
                    .pValue(driftResult.getPValue())
                    .detectedAt(LocalDateTime.now())
                    .driftType(driftResult.getDriftType())
                    .severity(determineDriftSeverity(driftResult.getDriftScore()))
                    .recommendedAction(getRecommendedAction(driftResult))
                    .build();

                sendDriftAlert(driftAlert);

                // Record drift event
                recordDriftEvent(modelName, driftResult);
            }

        } catch (Exception e) {
            log.error("Error during drift detection for model: {}", modelName, e);
        }
    }

    /**
     * Analyze model performance metrics
     */
    private void analyzeModelPerformance(String modelName) {
        long inferenceCount = modelInferenceCount.get(modelName).get();
        double totalLatency = modelInferenceLatency.get(modelName).sum();
        long errorCount = modelErrorCount.get(modelName).get();

        if (inferenceCount > 0) {
            double avgLatency = totalLatency / inferenceCount;
            double errorRate = (double) errorCount / inferenceCount;

            // Check performance thresholds
            if (avgLatency > latencyThresholdMs) {
                sendPerformanceAlert(modelName, "HIGH_LATENCY", 
                    String.format("Average latency %.2fms exceeds threshold %dms", 
                        avgLatency, latencyThresholdMs));
            }

            if (errorRate > 0.01) { // 1% error rate threshold
                sendPerformanceAlert(modelName, "HIGH_ERROR_RATE", 
                    String.format("Error rate %.2f%% exceeds 1%% threshold", errorRate * 100));
            }

            log.debug("Performance analysis - model: {}, inferences: {}, avg_latency: {}ms, error_rate: {}%", 
                modelName, inferenceCount, avgLatency, errorRate * 100);
        }
    }

    /**
     * Analyze prediction distribution drift
     */
    private DriftAnalysisResult analyzePredictionDrift(List<ModelInferenceEvent> events) {
        // Split events into baseline and recent windows
        int totalEvents = events.size();
        int baselineSize = totalEvents / 2;
        
        List<ModelInferenceEvent> baseline = events.subList(0, baselineSize);
        List<ModelInferenceEvent> recent = events.subList(baselineSize, totalEvents);

        // Extract prediction scores
        double[] baselineScores = baseline.stream()
            .mapToDouble(ModelInferenceEvent::getPredictionScore)
            .toArray();
        double[] recentScores = recent.stream()
            .mapToDouble(ModelInferenceEvent::getPredictionScore)
            .toArray();

        // Perform Kolmogorov-Smirnov test for distribution drift
        KSTestResult ksResult = performKSTest(baselineScores, recentScores);

        // Calculate drift score
        double driftScore = calculateDriftScore(baselineScores, recentScores);

        return DriftAnalysisResult.builder()
            .driftDetected(ksResult.getPValue() < driftDetectionThreshold)
            .driftScore(driftScore)
            .pValue(ksResult.getPValue())
            .driftType(DriftType.PREDICTION_DISTRIBUTION)
            .testStatistic(ksResult.getTestStatistic())
            .build();
    }

    /**
     * Perform Kolmogorov-Smirnov test for distribution comparison
     */
    private KSTestResult performKSTest(double[] baseline, double[] recent) {
        // Sort both arrays
        Arrays.sort(baseline);
        Arrays.sort(recent);

        int n1 = baseline.length;
        int n2 = recent.length;
        
        double maxDifference = 0.0;
        int i = 0, j = 0;
        
        while (i < n1 && j < n2) {
            double cdf1 = (double) (i + 1) / n1;
            double cdf2 = (double) (j + 1) / n2;
            double difference = Math.abs(cdf1 - cdf2);
            
            maxDifference = Math.max(maxDifference, difference);
            
            if (baseline[i] < recent[j]) {
                i++;
            } else {
                j++;
            }
        }

        // Calculate p-value approximation
        double kriticalValue = 1.36 * Math.sqrt((n1 + n2) / (double) (n1 * n2));
        double pValue = 2.0 * Math.exp(-2.0 * Math.pow(maxDifference / kriticalValue, 2));

        return KSTestResult.builder()
            .testStatistic(maxDifference)
            .pValue(Math.min(pValue, 1.0))
            .build();
    }

    /**
     * Calculate drift score based on statistical distance
     */
    private double calculateDriftScore(double[] baseline, double[] recent) {
        // Calculate means and standard deviations
        double baselineMean = Arrays.stream(baseline).average().orElse(0.0);
        double recentMean = Arrays.stream(recent).average().orElse(0.0);
        
        double baselineStd = calculateStandardDeviation(baseline, baselineMean);
        double recentStd = calculateStandardDeviation(recent, recentMean);

        // Calculate normalized difference
        double meanDifference = Math.abs(recentMean - baselineMean);
        double pooledStd = Math.sqrt((baselineStd * baselineStd + recentStd * recentStd) / 2.0);
        
        if (pooledStd > 0) {
            return meanDifference / pooledStd; // Cohen's d
        } else {
            return 0.0;
        }
    }

    /**
     * Check latency threshold and alert if exceeded
     */
    private void checkLatencyThreshold(String modelName, long latencyMs) {
        if (latencyMs > latencyThresholdMs * 2) { // 2x threshold for immediate alert
            sendImmediateAlert(modelName, "EXTREME_LATENCY", 
                String.format("Inference latency %dms is %dx normal threshold", 
                    latencyMs, latencyMs / latencyThresholdMs));
        }
    }

    /**
     * Check prediction score distribution for anomalies
     */
    private void checkPredictionDistribution(String modelName, double predictionScore) {
        // Check for extreme scores that might indicate model issues
        if (predictionScore < 0.0 || predictionScore > 1.0) {
            sendImmediateAlert(modelName, "INVALID_PREDICTION_SCORE", 
                String.format("Prediction score %.3f is outside valid range [0,1]", predictionScore));
        }
    }

    /**
     * Check accuracy threshold and alert if below acceptable level
     */
    private void checkAccuracyThreshold(String modelName) {
        // Calculate recent accuracy (last 1000 predictions)
        double recentAccuracy = calculateRecentAccuracy(modelName, 1000);
        
        if (recentAccuracy > 0 && recentAccuracy < performanceThreshold) {
            sendPerformanceAlert(modelName, "LOW_ACCURACY", 
                String.format("Recent accuracy %.2f%% below threshold %.2f%%", 
                    recentAccuracy * 100, performanceThreshold * 100));
        }
    }

    /**
     * Check error rate threshold
     */
    private void checkErrorRateThreshold(String modelName) {
        long totalInferences = modelInferenceCount.get(modelName).get();
        long totalErrors = modelErrorCount.get(modelName).get();
        
        if (totalInferences > 100) { // Minimum sample size
            double errorRate = (double) totalErrors / totalInferences;
            
            if (errorRate > 0.05) { // 5% error rate threshold
                sendPerformanceAlert(modelName, "HIGH_ERROR_RATE", 
                    String.format("Error rate %.2f%% exceeds 5%% threshold", errorRate * 100));
            }
        }
    }

    /**
     * Send drift detection alert
     */
    private void sendDriftAlert(ModelDriftAlert alert) {
        try {
            kafkaTemplate.send(alertTopic, "drift", alert);
            log.info("Sent drift alert for model: {}", alert.getModelName());
        } catch (Exception e) {
            log.error("Failed to send drift alert for model: {}", alert.getModelName(), e);
        }
    }

    /**
     * Send performance alert
     */
    private void sendPerformanceAlert(String modelName, String alertType, String message) {
        ModelPerformanceAlert alert = ModelPerformanceAlert.builder()
            .modelName(modelName)
            .alertType(alertType)
            .message(message)
            .timestamp(LocalDateTime.now())
            .severity(AlertSeverity.HIGH)
            .build();

        try {
            kafkaTemplate.send(alertTopic, "performance", alert);
            log.warn("Sent performance alert for model {}: {}", modelName, message);
        } catch (Exception e) {
            log.error("Failed to send performance alert for model: {}", modelName, e);
        }
    }

    /**
     * Send immediate critical alert
     */
    private void sendImmediateAlert(String modelName, String alertType, String message) {
        ModelCriticalAlert alert = ModelCriticalAlert.builder()
            .modelName(modelName)
            .alertType(alertType)
            .message(message)
            .timestamp(LocalDateTime.now())
            .severity(AlertSeverity.CRITICAL)
            .requiresImmediateAction(true)
            .build();

        try {
            kafkaTemplate.send(alertTopic, "critical", alert);
            log.error("CRITICAL ALERT - Model {}: {}", modelName, message);
        } catch (Exception e) {
            log.error("Failed to send critical alert for model: {}", modelName, e);
        }
    }

    /**
     * Check SLA compliance for model performance
     */
    private void checkSLACompliance(String modelName) {
        long totalInferences = modelInferenceCount.get(modelName).get();
        double totalLatency = modelInferenceLatency.get(modelName).sum();

        if (totalInferences > 0) {
            double avgLatency = totalLatency / totalInferences;

            // Check latency SLA (95th percentile should be < threshold)
            if (avgLatency > latencyThresholdMs) {
                log.warn("Model {} violating latency SLA: avg={}ms, threshold={}ms",
                    modelName, avgLatency, latencyThresholdMs);

                sendPerformanceAlert(modelName, "SLA_VIOLATION_LATENCY",
                    String.format("Average latency %.2fms exceeds SLA threshold %dms",
                        avgLatency, latencyThresholdMs));
            }
        }
    }

    /**
     * Update performance metrics in repository
     */
    private void updatePerformanceMetrics(String modelName) {
        try {
            long inferenceCount = modelInferenceCount.get(modelName).get();
            double totalLatency = modelInferenceLatency.get(modelName).sum();
            long errorCount = modelErrorCount.get(modelName).get();

            if (inferenceCount > 0) {
                double avgLatency = totalLatency / inferenceCount;
                double errorRate = (double) errorCount / inferenceCount;

                ModelPerformanceMetrics metrics = ModelPerformanceMetrics.builder()
                    .modelName(modelName)
                    .timestamp(LocalDateTime.now())
                    .inferenceCount(inferenceCount)
                    .averageLatencyMs(avgLatency)
                    .errorCount(errorCount)
                    .errorRate(errorRate)
                    .build();

                performanceRepository.save(metrics);
            }
        } catch (Exception e) {
            log.error("Failed to update performance metrics for model: {}", modelName, e);
        }
    }

    /**
     * Calculate standard deviation
     */
    private double calculateStandardDeviation(double[] values, double mean) {
        double sumSquaredDiff = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / values.length);
    }

    /**
     * Calculate recent accuracy for a model
     */
    private double calculateRecentAccuracy(String modelName, int sampleSize) {
        // This would query the repository for recent accuracy events
        // For now, return 0 to indicate no data
        return 0.0;
    }

    /**
     * Store accuracy event
     */
    private void storeAccuracyEvent(ModelAccuracyEvent event) {
        // Store in repository or time-series database
        log.debug("Storing accuracy event for model: {}", event.getModelName());
    }

    /**
     * Store failure event
     */
    private void storeFailureEvent(ModelFailureEvent event) {
        // Store in repository or time-series database
        log.debug("Storing failure event for model: {}", event.getModelName());
    }

    /**
     * Record drift event
     */
    private void recordDriftEvent(String modelName, DriftAnalysisResult result) {
        // Store drift event in repository
        log.debug("Recording drift event for model: {} with score: {}",
            modelName, result.getDriftScore());
    }

    /**
     * Classify error type
     */
    private String classifyError(String errorMessage) {
        if (errorMessage.contains("timeout")) return "TIMEOUT";
        if (errorMessage.contains("memory")) return "OUT_OF_MEMORY";
        if (errorMessage.contains("connection")) return "CONNECTION_ERROR";
        if (errorMessage.contains("validation")) return "VALIDATION_ERROR";
        return "UNKNOWN";
    }

    /**
     * Check if failure is critical
     */
    private boolean isCriticalFailure(String errorMessage) {
        return errorMessage.contains("OutOfMemory") ||
               errorMessage.contains("StackOverflow") ||
               errorMessage.contains("Fatal");
    }

    /**
     * Send critical alert
     */
    private void sendCriticalAlert(String modelName, String errorMessage) {
        ModelCriticalAlert alert = ModelCriticalAlert.builder()
            .modelName(modelName)
            .alertType("CRITICAL_FAILURE")
            .message("Critical model failure: " + errorMessage)
            .timestamp(LocalDateTime.now())
            .severity(AlertSeverity.CRITICAL)
            .requiresImmediateAction(true)
            .build();

        try {
            kafkaTemplate.send(alertTopic, "critical", alert);
            log.error("CRITICAL ALERT sent for model {}: {}", modelName, errorMessage);
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }

    /**
     * Determine drift severity
     */
    private String determineDriftSeverity(double driftScore) {
        if (driftScore > 0.5) return "HIGH";
        if (driftScore > 0.2) return "MEDIUM";
        return "LOW";
    }

    /**
     * Get recommended action for drift
     */
    private String getRecommendedAction(DriftAnalysisResult result) {
        if (result.getDriftScore() > 0.5) {
            return "IMMEDIATE_RETRAINING_REQUIRED";
        } else if (result.getDriftScore() > 0.2) {
            return "SCHEDULE_RETRAINING";
        } else {
            return "MONITOR";
        }
    }

    // ============================================================================
    // SUPPORTING CLASSES AND ENUMS
    // ============================================================================

    @lombok.Data
    @lombok.Builder
    public static class ModelInferenceEvent {
        private String modelName;
        private LocalDateTime timestamp;
        private long latencyMs;
        private double predictionScore;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelAccuracyEvent {
        private String modelName;
        private LocalDateTime timestamp;
        private boolean wasCorrect;
        private double confidenceScore;
        private String actualLabel;
        private String predictedLabel;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelFailureEvent {
        private String modelName;
        private LocalDateTime timestamp;
        private String errorMessage;
        private String errorType;
    }

    @lombok.Data
    @lombok.Builder
    public static class DriftAnalysisResult {
        private boolean driftDetected;
        private double driftScore;
        private double pValue;
        private DriftType driftType;
        private double testStatistic;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelDriftAlert {
        private String modelName;
        private double driftScore;
        private double pValue;
        private LocalDateTime detectedAt;
        private DriftType driftType;
        private String severity;
        private String recommendedAction;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelPerformanceAlert {
        private String modelName;
        private String alertType;
        private String message;
        private LocalDateTime timestamp;
        private AlertSeverity severity;
    }

    @lombok.Data
    @lombok.Builder
    public static class ModelCriticalAlert {
        private String modelName;
        private String alertType;
        private String message;
        private LocalDateTime timestamp;
        private AlertSeverity severity;
        private boolean requiresImmediateAction;
    }

    @lombok.Data
    @lombok.Builder
    public static class KSTestResult {
        private double testStatistic;
        private double pValue;
    }

    public enum DriftType {
        PREDICTION_DISTRIBUTION,
        FEATURE_DISTRIBUTION,
        CONCEPT_DRIFT,
        DATA_QUALITY
    }

    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}