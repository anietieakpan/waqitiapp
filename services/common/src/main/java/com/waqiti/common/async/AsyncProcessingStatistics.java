package com.waqiti.common.async;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for async processing
 */
@Data
@Builder
public class AsyncProcessingStatistics {
    
    private long totalTasksProcessed;
    private long currentlyRunning;
    private long completedTasks;
    private long failedTasks;
    private double successRate;
    private int heavyOpsQueueSize;
    private int ioOpsQueueSize;
    private int cpuOpsQueueSize;
    
    public long getTotalQueueSize() {
        return heavyOpsQueueSize + ioOpsQueueSize + cpuOpsQueueSize;
    }
    
    public boolean isHealthy() {
        return successRate > 90 && getTotalQueueSize() < 1000;
    }
    
    public String getHealthStatus() {
        if (successRate < 70) {
            return "CRITICAL - Low success rate";
        } else if (getTotalQueueSize() > 2000) {
            return "WARNING - High queue size";
        } else if (successRate < 90) {
            return "MODERATE - Average performance";
        } else {
            return "HEALTHY - Good performance";
        }
    }
}