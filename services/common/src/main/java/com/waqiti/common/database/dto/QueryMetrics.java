package com.waqiti.common.database.dto;

import lombok.Data;
import java.time.Instant;

/**
 * Query metrics for tracking performance.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class QueryMetrics {
    private long executionCount = 0;
    private long totalExecutionTime = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long totalResultSize = 0;
    private Instant lastExecution = Instant.now();
    
    public void incrementExecutionCount() {
        executionCount++;
    }
    
    public void incrementCacheHits() {
        cacheHits++;
    }
    
    public void updateExecutionTime(long executionTime) {
        totalExecutionTime += executionTime;
    }
    
    public void updateResultSize(int resultSize) {
        totalResultSize += resultSize;
    }
    
    public void recordExecution(boolean cacheHit, long executionTime, int resultSize) {
        executionCount++;
        totalExecutionTime += executionTime;
        totalResultSize += resultSize;
        lastExecution = Instant.now();
        
        if (cacheHit) {
            cacheHits++;
        } else {
            cacheMisses++;
        }
    }
    
    public double getAverageExecutionTime() {
        return executionCount > 0 ? (double) totalExecutionTime / executionCount : 0;
    }
    
    public double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 0;
    }
}