package com.waqiti.common.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive performance metrics for ML models in fraud detection.
 * Tracks accuracy, latency, throughput, and model health indicators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPerformanceMetrics {
    
    /**
     * Model accuracy (0.0 to 1.0)
     */
    @Builder.Default
    private double accuracy = 0.0;
    
    /**
     * Precision score (0.0 to 1.0)
     */
    @Builder.Default
    private double precision = 0.0;
    
    /**
     * Recall score (0.0 to 1.0)
     */
    @Builder.Default
    private double recall = 0.0;
    
    /**
     * F1 score (0.0 to 1.0)
     */
    @Builder.Default
    private double f1Score = 0.0;
    
    /**
     * Area Under the ROC Curve (0.0 to 1.0)
     */
    @Builder.Default
    private double auc = 0.0;
    
    /**
     * Total number of predictions made
     */
    @Builder.Default
    private long totalPredictions = 0L;
    
    /**
     * Average prediction latency in milliseconds
     */
    @Builder.Default
    private double averageLatency = 0.0;
    
    /**
     * Last update timestamp
     */
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    /**
     * True positive count
     */
    @Builder.Default
    private long truePositives = 0L;
    
    /**
     * True negative count
     */
    @Builder.Default
    private long trueNegatives = 0L;
    
    /**
     * False positive count
     */
    @Builder.Default
    private long falsePositives = 0L;
    
    /**
     * False negative count
     */
    @Builder.Default
    private long falseNegatives = 0L;
    
    /**
     * Total error count
     */
    @Builder.Default
    private long errorCount = 0L;
    
    /**
     * Minimum latency observed
     */
    @Builder.Default
    private double minLatency = Double.MAX_VALUE;
    
    /**
     * Maximum latency observed
     */
    @Builder.Default
    private double maxLatency = 0.0;
    
    /**
     * 95th percentile latency
     */
    @Builder.Default
    private double p95Latency = 0.0;
    
    /**
     * 99th percentile latency
     */
    @Builder.Default
    private double p99Latency = 0.0;
    
    /**
     * Throughput (predictions per second)
     */
    @Builder.Default
    private double throughput = 0.0;
    
    /**
     * Data drift score
     */
    @Builder.Default
    private double dataDriftScore = 0.0;
    
    /**
     * Model version being tracked
     */
    private String modelVersion;
    
    /**
     * Additional custom metrics
     */
    private Map<String, Double> customMetrics;
    
    /**
     * Performance trend over time
     */
    private Map<LocalDateTime, Double> accuracyTrend;
    
    /**
     * Thread-safe counter for predictions
     */
    @Builder.Default
    private final AtomicLong predictionCounter = new AtomicLong(0);
    
    /**
     * Thread-safe counter for errors
     */
    @Builder.Default
    private final AtomicLong errorCounter = new AtomicLong(0);
    
    /**
     * Increment prediction count thread-safely
     */
    public void incrementPredictions() {
        this.totalPredictions = predictionCounter.incrementAndGet();
    }
    
    /**
     * Increment error count thread-safely
     */
    public void incrementErrors() {
        this.errorCount = errorCounter.incrementAndGet();
    }
    
    /**
     * Update latency statistics
     */
    public void updateLatency(double latency) {
        this.minLatency = Math.min(this.minLatency, latency);
        this.maxLatency = Math.max(this.maxLatency, latency);
        
        // Update running average
        if (totalPredictions > 0) {
            this.averageLatency = ((this.averageLatency * (totalPredictions - 1)) + latency) / totalPredictions;
        } else {
            this.averageLatency = latency;
        }
        
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Calculate error rate
     */
    public double getErrorRate() {
        if (totalPredictions == 0) {
            return 0.0;
        }
        return (double) errorCount / totalPredictions;
    }
    
    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        return 1.0 - getErrorRate();
    }
    
    /**
     * Check if model performance is healthy
     */
    public boolean isHealthy() {
        return accuracy >= 0.8 && 
               getErrorRate() <= 0.05 && 
               averageLatency <= 100.0 && // 100ms threshold
               dataDriftScore <= 0.15; // 15% drift threshold
    }
    
    /**
     * Get performance grade
     */
    public PerformanceGrade getPerformanceGrade() {
        if (accuracy >= 0.95 && averageLatency <= 50.0) {
            return PerformanceGrade.EXCELLENT;
        } else if (accuracy >= 0.90 && averageLatency <= 100.0) {
            return PerformanceGrade.GOOD;
        } else if (accuracy >= 0.80 && averageLatency <= 200.0) {
            return PerformanceGrade.ACCEPTABLE;
        } else if (accuracy >= 0.70) {
            return PerformanceGrade.POOR;
        } else {
            return PerformanceGrade.UNACCEPTABLE;
        }
    }
    
    /**
     * Update confusion matrix values
     */
    public void updateConfusionMatrix(long tp, long tn, long fp, long fn) {
        this.truePositives = tp;
        this.trueNegatives = tn;
        this.falsePositives = fp;
        this.falseNegatives = fn;
        
        // Recalculate derived metrics
        recalculateMetrics();
    }
    
    /**
     * Recalculate derived metrics from confusion matrix
     */
    private void recalculateMetrics() {
        long total = truePositives + trueNegatives + falsePositives + falseNegatives;
        
        if (total > 0) {
            this.accuracy = (double) (truePositives + trueNegatives) / total;
        }
        
        if (truePositives + falsePositives > 0) {
            this.precision = (double) truePositives / (truePositives + falsePositives);
        }
        
        if (truePositives + falseNegatives > 0) {
            this.recall = (double) truePositives / (truePositives + falseNegatives);
        }
        
        if (precision + recall > 0) {
            this.f1Score = 2 * (precision * recall) / (precision + recall);
        }
        
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Get comprehensive performance summary
     */
    public String getPerformanceSummary() {
        return String.format(
            "Accuracy: %.3f, Precision: %.3f, Recall: %.3f, F1: %.3f, " +
            "AUC: %.3f, Avg Latency: %.1fms, Throughput: %.1f/s, " +
            "Error Rate: %.3f%%, Grade: %s, Healthy: %s",
            accuracy, precision, recall, f1Score, auc,
            averageLatency, throughput, getErrorRate() * 100,
            getPerformanceGrade(), isHealthy()
        );
    }
    
    /**
     * Create a snapshot of current metrics
     */
    public ModelPerformanceMetrics snapshot() {
        return ModelPerformanceMetrics.builder()
                .accuracy(this.accuracy)
                .precision(this.precision)
                .recall(this.recall)
                .f1Score(this.f1Score)
                .auc(this.auc)
                .totalPredictions(this.totalPredictions)
                .averageLatency(this.averageLatency)
                .errorCount(this.errorCount)
                .minLatency(this.minLatency)
                .maxLatency(this.maxLatency)
                .p95Latency(this.p95Latency)
                .p99Latency(this.p99Latency)
                .throughput(this.throughput)
                .dataDriftScore(this.dataDriftScore)
                .modelVersion(this.modelVersion)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    /**
     * Performance grade enumeration
     */
    public enum PerformanceGrade {
        EXCELLENT, GOOD, ACCEPTABLE, POOR, UNACCEPTABLE
    }
}