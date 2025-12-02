package com.waqiti.monitoring.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Statistical baseline for anomaly detection
 * Uses rolling window and Z-score calculation
 */
@Data
@Slf4j
public class MetricBaseline {
    private final String metricName;
    private final List<Double> values;
    private final int windowSize;
    private double mean;
    private double standardDeviation;
    private double min;
    private double max;
    private int sampleCount;
    private LocalDateTime lastUpdated;
    
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final int MIN_SAMPLES_FOR_BASELINE = 10;
    
    public MetricBaseline(String metricName) {
        this(metricName, DEFAULT_WINDOW_SIZE);
    }
    
    public MetricBaseline(String metricName, int windowSize) {
        this.metricName = metricName;
        this.windowSize = windowSize;
        this.values = new ArrayList<>();
        this.mean = 0.0;
        this.standardDeviation = 0.0;
        this.min = Double.MAX_VALUE;
        this.max = Double.MIN_VALUE;
        this.sampleCount = 0;
    }
    
    /**
     * Add a new value and update baseline statistics
     */
    public synchronized void addValue(double value) {
        values.add(value);
        sampleCount++;
        
        // Update min/max
        min = Math.min(min, value);
        max = Math.max(max, value);
        
        // Maintain rolling window
        if (values.size() > windowSize) {
            values.remove(0);
        }
        
        // Recalculate statistics
        recalculateStatistics();
        lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Calculate Z-score for a given value
     * Z-score = (value - mean) / standard_deviation
     */
    public double calculateZScore(double value) {
        if (standardDeviation == 0) {
            return 0;
        }
        return (value - mean) / standardDeviation;
    }
    
    /**
     * Check if we have enough data for reliable baseline
     */
    public boolean hasEnoughData() {
        return values.size() >= MIN_SAMPLES_FOR_BASELINE;
    }
    
    /**
     * Check if a value is an anomaly based on Z-score threshold
     */
    public boolean isAnomaly(double value, double threshold) {
        if (!hasEnoughData()) {
            return false;
        }
        
        double zscore = calculateZScore(value);
        return Math.abs(zscore) > threshold;
    }
    
    /**
     * Get percentile value
     */
    public double getPercentile(double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        
        return sorted.get(index);
    }
    
    /**
     * Recalculate mean and standard deviation
     */
    private void recalculateStatistics() {
        if (values.isEmpty()) {
            mean = 0;
            standardDeviation = 0;
            return;
        }
        
        // Calculate mean
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        mean = sum / values.size();
        
        // Calculate standard deviation
        double varianceSum = 0;
        for (double value : values) {
            varianceSum += Math.pow(value - mean, 2);
        }
        double variance = varianceSum / values.size();
        standardDeviation = Math.sqrt(variance);
    }
    
    /**
     * Get trend direction (-1: decreasing, 0: stable, 1: increasing)
     */
    public int getTrendDirection() {
        if (values.size() < 3) {
            return 0;
        }
        
        // Simple linear trend using recent values
        int recentCount = Math.min(10, values.size());
        List<Double> recent = values.subList(values.size() - recentCount, values.size());
        
        double firstHalfAvg = 0;
        double secondHalfAvg = 0;
        int halfSize = recentCount / 2;
        
        for (int i = 0; i < halfSize; i++) {
            firstHalfAvg += recent.get(i);
        }
        firstHalfAvg /= halfSize;
        
        for (int i = halfSize; i < recentCount; i++) {
            secondHalfAvg += recent.get(i);
        }
        secondHalfAvg /= (recentCount - halfSize);
        
        double difference = secondHalfAvg - firstHalfAvg;
        double threshold = standardDeviation * 0.5; // 50% of std dev as threshold
        
        if (difference > threshold) {
            return 1; // Increasing
        } else if (difference < -threshold) {
            return -1; // Decreasing
        } else {
            return 0; // Stable
        }
    }
    
    /**
     * Reset baseline
     */
    public void reset() {
        values.clear();
        mean = 0;
        standardDeviation = 0;
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
        sampleCount = 0;
        lastUpdated = null;
    }
    
    /**
     * Get baseline summary
     */
    public BaselineSummary getSummary() {
        return BaselineSummary.builder()
            .metricName(metricName)
            .mean(mean)
            .standardDeviation(standardDeviation)
            .min(min)
            .max(max)
            .sampleCount(sampleCount)
            .currentWindowSize(values.size())
            .p50(getPercentile(50))
            .p95(getPercentile(95))
            .p99(getPercentile(99))
            .trend(getTrendDirection())
            .lastUpdated(lastUpdated)
            .build();
    }
    
    @Data
    @lombok.Builder
    public static class BaselineSummary {
        private String metricName;
        private double mean;
        private double standardDeviation;
        private double min;
        private double max;
        private int sampleCount;
        private int currentWindowSize;
        private double p50;
        private double p95;
        private double p99;
        private int trend;
        private LocalDateTime lastUpdated;
    }
}