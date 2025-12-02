package com.waqiti.frauddetection.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * PRODUCTION-READY ML MODEL METRICS COLLECTOR
 * 
 * Collects and exposes ML model performance metrics for monitoring and alerting.
 * 
 * Metrics Categories:
 * 1. Prediction Metrics: latency, throughput, score distribution
 * 2. Model Performance: accuracy, precision, recall, F1 score
 * 3. Model Health: prediction rate, error rate, model age
 * 4. Business Metrics: fraud detection rate, false positive rate
 */
@Component
@Slf4j
public class ModelMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Prediction tracking
    private final Map<String, Double> recentPredictions = new ConcurrentHashMap<>();
    private final AtomicLong totalPredictions = new AtomicLong(0);
    private final AtomicLong predictionErrors = new AtomicLong(0);
    
    // Model performance metrics
    private volatile double currentAccuracy = 0.0;
    private volatile double currentPrecision = 0.0;
    private volatile double currentRecall = 0.0;
    private volatile double currentF1Score = 0.0;
    
    // Prediction score distribution
    private final DoubleAdder lowRiskScores = new DoubleAdder();
    private final DoubleAdder mediumRiskScores = new DoubleAdder();
    private final DoubleAdder highRiskScores = new DoubleAdder();
    
    // Timing metrics
    private volatile LocalDateTime lastPredictionTime;
    private volatile LocalDateTime modelLoadTime;
    
    // Micrometer metrics
    private Counter predictionCounter;
    private Counter errorCounter;
    private Timer predictionTimer;
    private Gauge accuracyGauge;
    private Gauge precisionGauge;
    private Gauge recallGauge;
    private Gauge f1ScoreGauge;
    
    public ModelMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void initializeMetrics() {
        // Initialize Micrometer metrics
        predictionCounter = Counter.builder("fraud_model_predictions_total")
                .description("Total number of fraud predictions made")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("fraud_model_errors_total")
                .description("Total number of prediction errors")
                .register(meterRegistry);
                
        predictionTimer = Timer.builder("fraud_model_prediction_duration")
                .description("Time taken for fraud prediction")
                .register(meterRegistry);
                
        accuracyGauge = Gauge.builder("fraud_model_accuracy")
                .description("Current model accuracy")
                .register(meterRegistry, this, ModelMetricsCollector::getCurrentAccuracy);
                
        precisionGauge = Gauge.builder("fraud_model_precision")
                .description("Current model precision")
                .register(meterRegistry, this, ModelMetricsCollector::getCurrentPrecision);
                
        recallGauge = Gauge.builder("fraud_model_recall")
                .description("Current model recall")
                .register(meterRegistry, this, ModelMetricsCollector::getCurrentRecall);
                
        f1ScoreGauge = Gauge.builder("fraud_model_f1_score")
                .description("Current model F1 score")
                .register(meterRegistry, this, ModelMetricsCollector::getCurrentF1Score);
        
        // Risk distribution gauges
        Gauge.builder("fraud_model_low_risk_predictions")
                .description("Number of low risk predictions")
                .register(meterRegistry, lowRiskScores, DoubleAdder::doubleValue);
                
        Gauge.builder("fraud_model_medium_risk_predictions")
                .description("Number of medium risk predictions")
                .register(meterRegistry, mediumRiskScores, DoubleAdder::doubleValue);
                
        Gauge.builder("fraud_model_high_risk_predictions")
                .description("Number of high risk predictions")
                .register(meterRegistry, highRiskScores, DoubleAdder::doubleValue);
        
        log.info("Fraud ML model metrics collector initialized");
    }
    
    /**
     * Record a prediction made by the model
     */
    public void recordPrediction(String transactionId, double score) {
        try {
            // Update counters
            predictionCounter.increment();
            totalPredictions.incrementAndGet();
            lastPredictionTime = LocalDateTime.now();
            
            // Store recent prediction (with cleanup)
            recentPredictions.put(transactionId, score);
            cleanupOldPredictions();
            
            // Update score distribution
            if (score < 0.3) {
                lowRiskScores.add(1.0);
            } else if (score < 0.7) {
                mediumRiskScores.add(1.0);
            } else {
                highRiskScores.add(1.0);
            }
            
            log.debug("Recorded prediction for transaction {}: score={}", transactionId, score);
            
        } catch (Exception e) {
            log.error("Error recording prediction metrics", e);
            recordError();
        }
    }
    
    /**
     * Record a prediction with timing information
     */
    public void recordPredictionWithTiming(String transactionId, double score, long durationMs) {
        recordPrediction(transactionId, score);
        
        // Record timing in Micrometer
        predictionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record a prediction error
     */
    public void recordError() {
        errorCounter.increment();
        predictionErrors.incrementAndGet();
        log.debug("Recorded prediction error");
    }
    
    /**
     * Update model performance metrics
     */
    public void updateMetrics(double accuracy, double precision, double recall, double f1Score) {
        this.currentAccuracy = accuracy;
        this.currentPrecision = precision;
        this.currentRecall = recall;
        this.currentF1Score = f1Score;
        
        log.info("Updated model metrics - Accuracy: {:.3f}, Precision: {:.3f}, Recall: {:.3f}, F1: {:.3f}",
                accuracy, precision, recall, f1Score);
        
        // Alert if metrics drop below thresholds
        if (accuracy < 0.85) {
            log.warn("ALERT: Model accuracy below threshold: {:.3f} < 0.85", accuracy);
        }
        if (precision < 0.80) {
            log.warn("ALERT: Model precision below threshold: {:.3f} < 0.80", precision);
        }
        if (recall < 0.70) {
            log.warn("ALERT: Model recall below threshold: {:.3f} < 0.70", recall);
        }
    }
    
    /**
     * Record model load time
     */
    public void recordModelLoad() {
        this.modelLoadTime = LocalDateTime.now();
        log.info("Recorded model load time: {}", modelLoadTime);
    }
    
    /**
     * Get comprehensive metrics summary
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Basic metrics
        metrics.put("total_predictions", totalPredictions.get());
        metrics.put("prediction_errors", predictionErrors.get());
        metrics.put("error_rate", calculateErrorRate());
        
        // Model performance
        metrics.put("accuracy", currentAccuracy);
        metrics.put("precision", currentPrecision);
        metrics.put("recall", currentRecall);
        metrics.put("f1_score", currentF1Score);
        
        // Score distribution
        metrics.put("low_risk_predictions", lowRiskScores.doubleValue());
        metrics.put("medium_risk_predictions", mediumRiskScores.doubleValue());
        metrics.put("high_risk_predictions", highRiskScores.doubleValue());
        
        // Timing information
        metrics.put("last_prediction_time", lastPredictionTime);
        metrics.put("model_load_time", modelLoadTime);
        
        // Health indicators
        metrics.put("predictions_per_minute", calculatePredictionsPerMinute());
        metrics.put("recent_predictions_count", recentPredictions.size());
        
        return metrics;
    }
    
    /**
     * Get model health status
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        boolean isHealthy = true;
        StringBuilder issues = new StringBuilder();
        
        // Check error rate
        double errorRate = calculateErrorRate();
        if (errorRate > 0.05) { // 5% error threshold
            isHealthy = false;
            issues.append("High error rate: ").append(String.format("%.2f%%", errorRate * 100)).append("; ");
        }
        
        // Check if model is making predictions
        if (lastPredictionTime == null || 
            lastPredictionTime.isBefore(LocalDateTime.now().minusMinutes(10))) {
            isHealthy = false;
            issues.append("No recent predictions; ");
        }
        
        // Check model performance
        if (currentAccuracy < 0.85) {
            isHealthy = false;
            issues.append("Low accuracy: ").append(String.format("%.3f", currentAccuracy)).append("; ");
        }
        
        health.put("healthy", isHealthy);
        health.put("issues", issues.toString());
        health.put("error_rate", errorRate);
        health.put("last_prediction", lastPredictionTime);
        health.put("predictions_per_minute", calculatePredictionsPerMinute());
        
        return health;
    }
    
    /**
     * Get prediction score statistics
     */
    public Map<String, Object> getScoreStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        if (recentPredictions.isEmpty()) {
            stats.put("count", 0);
            return stats;
        }
        
        double[] scores = recentPredictions.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        
        stats.put("count", scores.length);
        stats.put("min", java.util.Arrays.stream(scores).min().orElse(0.0));
        stats.put("max", java.util.Arrays.stream(scores).max().orElse(0.0));
        stats.put("average", java.util.Arrays.stream(scores).average().orElse(0.0));
        stats.put("median", calculateMedian(scores));
        
        // Risk distribution percentages
        double total = scores.length;
        stats.put("low_risk_percentage", (lowRiskScores.doubleValue() / total) * 100);
        stats.put("medium_risk_percentage", (mediumRiskScores.doubleValue() / total) * 100);
        stats.put("high_risk_percentage", (highRiskScores.doubleValue() / total) * 100);
        
        return stats;
    }
    
    // PRIVATE HELPER METHODS
    
    private void cleanupOldPredictions() {
        // Keep only recent predictions (max 10,000)
        if (recentPredictions.size() > 10000) {
            // Remove oldest entries (this is a simple cleanup, could be optimized)
            int toRemove = recentPredictions.size() - 8000;
            recentPredictions.entrySet().stream()
                    .limit(toRemove)
                    .map(Map.Entry::getKey)
                    .forEach(recentPredictions::remove);
        }
    }
    
    private double calculateErrorRate() {
        long total = totalPredictions.get();
        if (total == 0) return 0.0;
        return (double) predictionErrors.get() / total;
    }
    
    private double calculatePredictionsPerMinute() {
        if (lastPredictionTime == null || modelLoadTime == null) {
            return 0.0;
        }
        
        long minutesSinceLoad = java.time.Duration.between(modelLoadTime, LocalDateTime.now()).toMinutes();
        if (minutesSinceLoad == 0) return 0.0;
        
        return (double) totalPredictions.get() / minutesSinceLoad;
    }
    
    private double calculateMedian(double[] scores) {
        if (scores.length == 0) return 0.0;
        
        java.util.Arrays.sort(scores);
        int middle = scores.length / 2;
        
        if (scores.length % 2 == 0) {
            return (scores[middle - 1] + scores[middle]) / 2.0;
        } else {
            return scores[middle];
        }
    }
    
    // GETTERS FOR MICROMETER GAUGES
    
    public double getCurrentAccuracy() {
        return currentAccuracy;
    }
    
    public double getCurrentPrecision() {
        return currentPrecision;
    }
    
    public double getCurrentRecall() {
        return currentRecall;
    }
    
    public double getCurrentF1Score() {
        return currentF1Score;
    }
    
    /**
     * Reset all metrics (useful for testing)
     */
    public void reset() {
        recentPredictions.clear();
        totalPredictions.set(0);
        predictionErrors.set(0);
        currentAccuracy = 0.0;
        currentPrecision = 0.0;
        currentRecall = 0.0;
        currentF1Score = 0.0;
        lowRiskScores.reset();
        mediumRiskScores.reset();
        highRiskScores.reset();
        lastPredictionTime = null;
        modelLoadTime = null;
        
        log.info("Fraud ML model metrics reset");
    }
}