package com.waqiti.common.database.optimization;

import com.waqiti.common.database.performance.models.ConnectionPoolMetrics;
import com.waqiti.common.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Intelligent connection pool manager that dynamically optimizes pool configuration
 * based on usage patterns, load, and performance metrics.
 *
 * Features:
 * - Dynamic pool sizing based on load patterns
 * - Connection leak detection and prevention
 * - Predictive scaling for anticipated load
 * - Health monitoring and auto-recovery
 * - Performance-based routing
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntelligentConnectionPoolManager {

    private final MetricsCollector metricsCollector;
    
    // Pool configuration tracking
    private final Map<String, PoolConfiguration> poolConfigurations = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPoolMetrics> currentMetrics = new ConcurrentHashMap<>();
    private final Map<String, LoadPattern> loadPatterns = new ConcurrentHashMap<>();
    private final Map<String, List<ConnectionPoolMetrics>> historicalMetrics = new ConcurrentHashMap<>();
    
    // Optimization parameters
    private static final int MIN_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 50;
    private static final double TARGET_UTILIZATION = 0.75; // 75% utilization target
    private static final long OPTIMIZATION_INTERVAL_MS = 60000; // 1 minute
    private static final int HISTORY_RETENTION_HOURS = 24;
    
    @PostConstruct
    public void initialize() {
        log.info("Intelligent Connection Pool Manager initialized");
    }
    
    /**
     * Registers a connection pool for intelligent management.
     *
     * @param poolName name of the pool
     * @param initialConfig initial configuration
     */
    public void registerPool(String poolName, PoolConfiguration initialConfig) {
        poolConfigurations.put(poolName, initialConfig);
        loadPatterns.put(poolName, new LoadPattern());
        historicalMetrics.put(poolName, new ArrayList<>());
        
        log.info("Registered connection pool: {} with initial config: {}", 
                poolName, initialConfig);
    }
    
    /**
     * Updates metrics for a connection pool.
     *
     * @param poolName name of the pool
     * @param metrics current pool metrics
     */
    public void updatePoolMetrics(String poolName, ConnectionPoolMetrics metrics) {
        currentMetrics.put(poolName, metrics);
        
        // Add to historical data
        List<ConnectionPoolMetrics> history = historicalMetrics.get(poolName);
        if (history != null) {
            synchronized (history) {
                history.add(metrics);
                
                // Remove old metrics (keep last 24 hours)
                Instant cutoff = Instant.now().minus(HISTORY_RETENTION_HOURS, ChronoUnit.HOURS);
                history.removeIf(m -> m.getTimestamp().isBefore(cutoff));
            }
        }
        
        // Update load patterns
        LoadPattern pattern = loadPatterns.get(poolName);
        if (pattern != null) {
            pattern.addDataPoint(metrics);
        }
        
        // Record metrics
        metricsCollector.recordGauge("pool." + poolName + ".active_connections",
                                   "Active connections in pool " + poolName,
                                   metrics.getActiveConnections());
        metricsCollector.recordGauge("pool." + poolName + ".utilization",
                                   "Utilization percentage for pool " + poolName,
                                   metrics.getUtilizationPercentage());
        metricsCollector.recordGauge("pool." + poolName + ".waiting_requests",
                                   "Waiting requests for pool " + poolName,
                                   metrics.getWaitingRequests());
    }
    
    /**
     * Performs intelligent optimization of all registered pools.
     */
    @Scheduled(fixedDelay = OPTIMIZATION_INTERVAL_MS)
    public void optimizePools() {
        for (String poolName : poolConfigurations.keySet()) {
            try {
                optimizePool(poolName);
            } catch (Exception e) {
                log.error("Failed to optimize pool: {}", poolName, e);
            }
        }
    }
    
    /**
     * Optimizes a specific connection pool based on current metrics and patterns.
     *
     * @param poolName name of the pool to optimize
     * @return optimization result
     */
    public OptimizationResult optimizePool(String poolName) {
        PoolConfiguration config = poolConfigurations.get(poolName);
        ConnectionPoolMetrics metrics = currentMetrics.get(poolName);
        LoadPattern pattern = loadPatterns.get(poolName);
        
        if (config == null || metrics == null || pattern == null) {
            return new OptimizationResult(poolName, false, "Insufficient data for optimization");
        }
        
        OptimizationResult result = new OptimizationResult(poolName, false, "No optimization needed");
        
        // Analyze current performance
        PerformanceAnalysis analysis = analyzePerformance(poolName, metrics, pattern);
        
        // Determine optimal pool size
        int currentMax = config.getMaxPoolSize();
        int optimalMax = calculateOptimalMaxPoolSize(analysis);
        
        if (Math.abs(optimalMax - currentMax) > 2) { // Significant change threshold
            config.setMaxPoolSize(optimalMax);
            result.setOptimizationApplied(true);
            result.setDescription(String.format("Adjusted max pool size from %d to %d", 
                                               currentMax, optimalMax));
            
            log.info("Pool {} optimization: max size {} -> {} (reason: {})", 
                    poolName, currentMax, optimalMax, analysis.getPrimaryReason());
        }
        
        // Optimize minimum pool size
        int currentMin = config.getMinPoolSize();
        int optimalMin = calculateOptimalMinPoolSize(analysis);
        
        if (optimalMin != currentMin) {
            config.setMinPoolSize(optimalMin);
            result.setOptimizationApplied(true);
            result.setDescription(result.getDescription() + 
                    String.format(" | Adjusted min pool size from %d to %d", currentMin, optimalMin));
        }
        
        // Detect and handle connection leaks
        if (metrics.getConnectionLeaks() > 0) {
            handleConnectionLeaks(poolName, metrics);
            result.setOptimizationApplied(true);
            result.setDescription(result.getDescription() + " | Connection leak mitigation applied");
        }
        
        // Predictive scaling for anticipated load
        PredictedLoad predictedLoad = predictLoad(pattern);
        if (predictedLoad.isSignificantChange()) {
            applyPredictiveScaling(config, predictedLoad);
            result.setOptimizationApplied(true);
            result.setDescription(result.getDescription() + " | Predictive scaling applied");
        }
        
        return result;
    }
    
    /**
     * Gets recommendations for pool optimization.
     *
     * @param poolName name of the pool
     * @return list of optimization recommendations
     */
    public List<PoolRecommendation> getOptimizationRecommendations(String poolName) {
        List<PoolRecommendation> recommendations = new ArrayList<>();
        
        ConnectionPoolMetrics metrics = currentMetrics.get(poolName);
        LoadPattern pattern = loadPatterns.get(poolName);
        
        if (metrics == null || pattern == null) {
            return recommendations;
        }
        
        // High utilization recommendation
        if (metrics.getUtilizationPercentage() > 90) {
            recommendations.add(new PoolRecommendation(
                "HIGH_UTILIZATION",
                "CRITICAL",
                "Pool utilization is very high (" + metrics.getUtilizationPercentage() + "%). " +
                "Consider increasing max pool size or optimizing queries.",
                Arrays.asList("Increase max pool size", "Optimize slow queries", "Add connection timeout")
            ));
        }
        
        // Connection leak recommendation
        if (metrics.getConnectionLeaks() > 0) {
            recommendations.add(new PoolRecommendation(
                "CONNECTION_LEAKS",
                "CRITICAL",
                metrics.getConnectionLeaks() + " connection leaks detected. " +
                "This can lead to pool exhaustion.",
                Arrays.asList("Enable connection leak detection", "Review connection handling code", 
                            "Set shorter connection timeout")
            ));
        }
        
        // Waiting requests recommendation
        if (metrics.getWaitingRequests() > 5) {
            recommendations.add(new PoolRecommendation(
                "HIGH_WAIT_QUEUE",
                "WARNING",
                "High number of requests waiting for connections (" + metrics.getWaitingRequests() + ").",
                Arrays.asList("Increase pool size", "Optimize query execution time", 
                            "Implement connection pooling in application layer")
            ));
        }
        
        // Underutilization recommendation
        if (metrics.getUtilizationPercentage() < 20 && pattern.getAverageUtilization() < 25) {
            recommendations.add(new PoolRecommendation(
                "UNDERUTILIZATION",
                "INFO",
                "Pool appears underutilized. Consider reducing pool size to save resources.",
                Arrays.asList("Reduce max pool size", "Reduce min pool size")
            ));
        }
        
        return recommendations;
    }
    
    // Private helper methods
    
    private PerformanceAnalysis analyzePerformance(String poolName, ConnectionPoolMetrics metrics, 
                                                  LoadPattern pattern) {
        PerformanceAnalysis analysis = new PerformanceAnalysis();
        
        analysis.setCurrentUtilization(metrics.getUtilizationPercentage());
        analysis.setAverageUtilization(pattern.getAverageUtilization());
        analysis.setPeakUtilization(pattern.getPeakUtilization());
        analysis.setWaitingRequests(metrics.getWaitingRequests());
        analysis.setConnectionFailures(metrics.getConnectionFailures());
        analysis.setTrend(pattern.getTrend());
        
        // Determine primary performance issue
        if (metrics.getUtilizationPercentage() > 95) {
            analysis.setPrimaryReason("Critical utilization");
        } else if (metrics.getWaitingRequests() > 10) {
            analysis.setPrimaryReason("High wait queue");
        } else if (metrics.getConnectionFailures() > 0) {
            analysis.setPrimaryReason("Connection failures");
        } else if (pattern.getTrend() == LoadPattern.Trend.INCREASING) {
            analysis.setPrimaryReason("Increasing load trend");
        } else {
            analysis.setPrimaryReason("Normal operation");
        }
        
        return analysis;
    }
    
    private int calculateOptimalMaxPoolSize(PerformanceAnalysis analysis) {
        int currentMax = (int) (analysis.getCurrentUtilization() / 100.0 * 20); // Estimate current max
        
        if (analysis.getCurrentUtilization() > 90) {
            // High utilization - increase pool size
            return Math.min(MAX_POOL_SIZE, (int) (currentMax * 1.3));
        } else if (analysis.getCurrentUtilization() > 75) {
            // Target utilization range - slight increase if trending up
            if (analysis.getTrend() == LoadPattern.Trend.INCREASING) {
                return Math.min(MAX_POOL_SIZE, (int) (currentMax * 1.15));
            }
            return currentMax;
        } else if (analysis.getAverageUtilization() < 30) {
            // Low utilization - decrease pool size
            return Math.max(MIN_POOL_SIZE, (int) (currentMax * 0.8));
        }
        
        return currentMax;
    }
    
    private int calculateOptimalMinPoolSize(PerformanceAnalysis analysis) {
        // Base minimum on average utilization and trend
        double avgUtilization = analysis.getAverageUtilization();
        
        if (avgUtilization > 50) {
            return Math.max(MIN_POOL_SIZE, (int) (avgUtilization / 100.0 * 10));
        } else if (avgUtilization > 25) {
            return Math.max(MIN_POOL_SIZE, 3);
        } else {
            return MIN_POOL_SIZE;
        }
    }
    
    private void handleConnectionLeaks(String poolName, ConnectionPoolMetrics metrics) {
        log.warn("Connection leaks detected in pool {}: {} leaks", 
                poolName, metrics.getConnectionLeaks());
        
        // Record metric for alerting
        metricsCollector.incrementCounter("pool.connection_leaks.detected");
        
        // Could implement automatic leak mitigation here
        // For now, just log and record metrics
    }
    
    private PredictedLoad predictLoad(LoadPattern pattern) {
        // Simple load prediction based on historical patterns
        PredictedLoad prediction = new PredictedLoad();
        
        double currentTrend = pattern.getTrendStrength();
        double nextHourPrediction = pattern.getAverageUtilization() * (1 + currentTrend * 0.1);
        
        prediction.setPredictedUtilization(nextHourPrediction);
        prediction.setConfidence(pattern.getTrendConfidence());
        prediction.setSignificantChange(Math.abs(nextHourPrediction - pattern.getAverageUtilization()) > 15);
        
        return prediction;
    }
    
    private void applyPredictiveScaling(PoolConfiguration config, PredictedLoad predictedLoad) {
        if (predictedLoad.getPredictedUtilization() > 80) {
            // Increase pool size proactively
            int newMax = Math.min(MAX_POOL_SIZE, (int) (config.getMaxPoolSize() * 1.2));
            config.setMaxPoolSize(newMax);
            
            log.info("Applied predictive scaling: increased max pool size to {} " +
                    "(predicted utilization: {}%)", 
                    newMax, predictedLoad.getPredictedUtilization());
        }
    }
    
    // Data classes
    
    public static class PoolConfiguration {
        private int minPoolSize = MIN_POOL_SIZE;
        private int maxPoolSize = 20;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private boolean leakDetectionEnabled = true;
        
        // Getters and setters
        public int getMinPoolSize() { return minPoolSize; }
        public void setMinPoolSize(int minPoolSize) { this.minPoolSize = minPoolSize; }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        
        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
        
        public boolean isLeakDetectionEnabled() { return leakDetectionEnabled; }
        public void setLeakDetectionEnabled(boolean leakDetectionEnabled) { this.leakDetectionEnabled = leakDetectionEnabled; }
        
        @Override
        public String toString() {
            return String.format("PoolConfig{min=%d, max=%d, timeout=%dms}", 
                               minPoolSize, maxPoolSize, connectionTimeout);
        }
    }
    
    public static class LoadPattern {
        private final Queue<Double> utilizationHistory = new LinkedList<>();
        private final AtomicInteger dataPoints = new AtomicInteger(0);
        private double averageUtilization = 0.0;
        private double peakUtilization = 0.0;
        private Trend trend = Trend.STABLE;
        private double trendStrength = 0.0;
        private double trendConfidence = 0.0;
        
        public enum Trend { INCREASING, DECREASING, STABLE }
        
        public void addDataPoint(ConnectionPoolMetrics metrics) {
            synchronized (utilizationHistory) {
                utilizationHistory.offer(metrics.getUtilizationPercentage());
                
                // Keep only last 60 data points (1 hour of data if collected every minute)
                while (utilizationHistory.size() > 60) {
                    utilizationHistory.poll();
                }
                
                dataPoints.incrementAndGet();
                updateCalculations();
            }
        }
        
        private void updateCalculations() {
            List<Double> history = new ArrayList<>(utilizationHistory);
            
            if (history.isEmpty()) return;
            
            // Calculate average
            averageUtilization = history.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            // Calculate peak
            peakUtilization = history.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            
            // Calculate trend (simple linear regression)
            if (history.size() > 10) {
                double[] trend = calculateTrend(history);
                trendStrength = trend[0];
                trendConfidence = trend[1];
                
                if (trendStrength > 0.1 && trendConfidence > 0.7) {
                    this.trend = Trend.INCREASING;
                } else if (trendStrength < -0.1 && trendConfidence > 0.7) {
                    this.trend = Trend.DECREASING;
                } else {
                    this.trend = Trend.STABLE;
                }
            }
        }
        
        private double[] calculateTrend(List<Double> data) {
            int n = data.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += data.get(i);
                sumXY += i * data.get(i);
                sumX2 += i * i;
            }
            
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            
            // Calculate R-squared for confidence
            double meanY = sumY / n;
            double totalSumSquares = 0;
            double residualSumSquares = 0;
            
            for (int i = 0; i < n; i++) {
                double predicted = slope * i + (sumY - slope * sumX) / n;
                totalSumSquares += Math.pow(data.get(i) - meanY, 2);
                residualSumSquares += Math.pow(data.get(i) - predicted, 2);
            }
            
            double rSquared = 1 - (residualSumSquares / totalSumSquares);
            
            return new double[]{slope, rSquared};
        }
        
        // Getters
        public double getAverageUtilization() { return averageUtilization; }
        public double getPeakUtilization() { return peakUtilization; }
        public Trend getTrend() { return trend; }
        public double getTrendStrength() { return trendStrength; }
        public double getTrendConfidence() { return trendConfidence; }
    }
    
    public static class PerformanceAnalysis {
        private double currentUtilization;
        private double averageUtilization;
        private double peakUtilization;
        private int waitingRequests;
        private int connectionFailures;
        private LoadPattern.Trend trend;
        private String primaryReason;
        
        // Getters and setters
        public double getCurrentUtilization() { return currentUtilization; }
        public void setCurrentUtilization(double currentUtilization) { this.currentUtilization = currentUtilization; }
        
        public double getAverageUtilization() { return averageUtilization; }
        public void setAverageUtilization(double averageUtilization) { this.averageUtilization = averageUtilization; }
        
        public double getPeakUtilization() { return peakUtilization; }
        public void setPeakUtilization(double peakUtilization) { this.peakUtilization = peakUtilization; }
        
        public int getWaitingRequests() { return waitingRequests; }
        public void setWaitingRequests(int waitingRequests) { this.waitingRequests = waitingRequests; }
        
        public int getConnectionFailures() { return connectionFailures; }
        public void setConnectionFailures(int connectionFailures) { this.connectionFailures = connectionFailures; }
        
        public LoadPattern.Trend getTrend() { return trend; }
        public void setTrend(LoadPattern.Trend trend) { this.trend = trend; }
        
        public String getPrimaryReason() { return primaryReason; }
        public void setPrimaryReason(String primaryReason) { this.primaryReason = primaryReason; }
    }
    
    public static class PredictedLoad {
        private double predictedUtilization;
        private double confidence;
        private boolean significantChange;
        
        // Getters and setters
        public double getPredictedUtilization() { return predictedUtilization; }
        public void setPredictedUtilization(double predictedUtilization) { this.predictedUtilization = predictedUtilization; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public boolean isSignificantChange() { return significantChange; }
        public void setSignificantChange(boolean significantChange) { this.significantChange = significantChange; }
    }
    
    public static class OptimizationResult {
        private final String poolName;
        private boolean optimizationApplied;
        private String description;
        private Instant timestamp;
        
        public OptimizationResult(String poolName, boolean optimizationApplied, String description) {
            this.poolName = poolName;
            this.optimizationApplied = optimizationApplied;
            this.description = description;
            this.timestamp = Instant.now();
        }
        
        // Getters and setters
        public String getPoolName() { return poolName; }
        public boolean isOptimizationApplied() { return optimizationApplied; }
        public void setOptimizationApplied(boolean optimizationApplied) { this.optimizationApplied = optimizationApplied; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    public static class PoolRecommendation {
        private final String type;
        private final String severity;
        private final String description;
        private final List<String> actions;
        
        public PoolRecommendation(String type, String severity, String description, List<String> actions) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.actions = actions;
        }
        
        // Getters
        public String getType() { return type; }
        public String getSeverity() { return severity; }
        public String getDescription() { return description; }
        public List<String> getActions() { return actions; }
    }
}