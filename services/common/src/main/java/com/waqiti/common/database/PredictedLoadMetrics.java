package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents predicted database load metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictedLoadMetrics {
    private Instant predictionTime;
    private Instant targetTime;
    private double predictedQps; // Queries per second
    private double predictedCpuUsage;
    private double predictedMemoryUsage;
    private double predictedIoUsage;
    private int predictedActiveConnections;
    private Map<String, Double> queryTypeDistribution;
    private double confidence;
    private String predictionModel;
    private double totalPredictedQueries;
    
    public double getPeakConcurrentQueries() {
        return predictedActiveConnections * 1.5; // Estimate peak as 1.5x average
    }
    
    public double getEstimatedCpuUsage() {
        return predictedCpuUsage;
    }
    
    public double getEstimatedMemoryUsage() {
        return predictedMemoryUsage;
    }
    
    public long getEstimatedIoOperations() {
        return (long)(predictedIoUsage * 1000);
    }
    
    public double getReadWriteRatio() {
        // Calculate read/write ratio from query type distribution
        if (queryTypeDistribution == null || queryTypeDistribution.isEmpty()) {
            return 2.0; // Default ratio
        }
        
        double readQueries = queryTypeDistribution.getOrDefault("SELECT", 0.0) +
                            queryTypeDistribution.getOrDefault("READ", 0.0);
        double writeQueries = queryTypeDistribution.getOrDefault("INSERT", 0.0) +
                             queryTypeDistribution.getOrDefault("UPDATE", 0.0) +
                             queryTypeDistribution.getOrDefault("DELETE", 0.0) +
                             queryTypeDistribution.getOrDefault("WRITE", 0.0);
        
        return writeQueries > 0 ? readQueries / writeQueries : readQueries;
    }
    
    public long getAverageQueryDurationMs() {
        // Estimate based on predicted QPS and active connections
        if (predictedQps > 0) {
            return (long)((predictedActiveConnections * 1000.0) / predictedQps);
        }
        return 100; // Default 100ms
    }
}