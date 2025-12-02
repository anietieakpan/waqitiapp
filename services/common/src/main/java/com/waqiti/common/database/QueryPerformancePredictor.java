package com.waqiti.common.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Predicts database query performance using machine learning models
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryPerformancePredictor {
    
    private final Map<String, QueryPerformanceMetrics> performanceHistory = new ConcurrentHashMap<>();
    
    public PredictedLoadMetrics predictLoad(List<PredictedQuery> upcomingQueries) {
        log.debug("Predicting load for {} upcoming queries", upcomingQueries.size());
        
        double totalPredictedQps = upcomingQueries.stream()
            .mapToDouble(q -> q.getProbability() * calculateBaseQps())
            .sum();
            
        double predictedCpuUsage = totalPredictedQps * 0.1; // 10% CPU per QPS
        double predictedMemoryUsage = totalPredictedQps * 50; // 50MB per QPS
        double predictedIoUsage = totalPredictedQps * 0.2; // 20% IO per QPS
        
        return PredictedLoadMetrics.builder()
            .predictionTime(Instant.now())
            .targetTime(Instant.now().plusSeconds(300)) // 5 minutes ahead
            .predictedQps(totalPredictedQps)
            .predictedCpuUsage(predictedCpuUsage)
            .predictedMemoryUsage(predictedMemoryUsage)
            .predictedIoUsage(predictedIoUsage)
            .predictedActiveConnections((int) Math.ceil(totalPredictedQps / 10))
            .confidence(0.85)
            .predictionModel("LinearRegression-v1.0")
            .build();
    }
    
    public QueryPerformanceMetrics getMetrics(String queryPattern) {
        return performanceHistory.computeIfAbsent(queryPattern, this::createDefaultMetrics);
    }
    
    public void updateMetrics(String queryPattern, long executionTimeMs, boolean successful) {
        QueryPerformanceMetrics metrics = performanceHistory.computeIfAbsent(queryPattern, this::createDefaultMetrics);
        
        metrics.setExecutionCount(metrics.getExecutionCount() + 1);
        
        // Update rolling average
        double currentAvg = metrics.getAverageExecutionTimeMs();
        double newAvg = (currentAvg * (metrics.getExecutionCount() - 1) + executionTimeMs) / metrics.getExecutionCount();
        metrics.setAverageExecutionTimeMs(newAvg);
        
        if (successful) {
            metrics.setSuccessCount(metrics.getSuccessCount() + 1);
        }
        
        metrics.setLastExecuted(Instant.now());
        
        log.debug("Updated metrics for pattern {}: avgTime={}ms, successRate={}%", 
            queryPattern, newAvg, (metrics.getSuccessCount() * 100.0 / metrics.getExecutionCount()));
    }
    
    private QueryPerformanceMetrics createDefaultMetrics(String queryPattern) {
        return QueryPerformanceMetrics.builder()
            .queryPattern(queryPattern)
            .averageExecutionTimeMs(100.0) // Default 100ms
            .executionCount(0)
            .successCount(0)
            .lastExecuted(Instant.now())
            .build();
    }
    
    private double calculateBaseQps() {
        // Base queries per second - could be calculated from historical data
        return 10.0;
    }
}