package com.waqiti.common.batch;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for batch processing operations
 */
@Data
@Builder
public class BatchProcessingStatistics {
    
    private long totalBatchJobs;
    private long runningJobs;
    private long completedJobs;
    private long failedJobs;
    private double successRate;
    private long totalItemsProcessed;
    private double averageProcessingTime;
    private int queueSize;
    
    public boolean isHealthy() {
        return successRate > 90 && queueSize < 1000;
    }
    
    public String getHealthStatus() {
        if (successRate < 70) {
            return "CRITICAL - Low success rate";
        } else if (queueSize > 2000) {
            return "WARNING - High queue size";
        } else if (successRate < 90) {
            return "MODERATE - Average performance";
        } else {
            return "HEALTHY - Good performance";
        }
    }
    
    public double getThroughput() {
        if (averageProcessingTime <= 0) return 0;
        return totalItemsProcessed / averageProcessingTime * 1000; // items per second
    }
}