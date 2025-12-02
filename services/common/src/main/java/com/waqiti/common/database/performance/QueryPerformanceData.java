package com.waqiti.common.database.performance;

import lombok.Data;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data class for tracking query performance metrics.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class QueryPerformanceData {
    
    private final AtomicLong executionCount = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong maxExecutionTime = new AtomicLong(0);
    private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong totalResultCount = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicReference<Instant> lastExecution = new AtomicReference<>(Instant.now());
    
    private volatile double averageExecutionTime = 0.0;
    private volatile double averageResultCount = 0.0;
    
    public void incrementExecutions() {
        executionCount.incrementAndGet();
    }
    
    public void addExecutionTime(long timeMs) {
        totalExecutionTime.addAndGet(timeMs);
        
        // Update min/max
        long currentMax = maxExecutionTime.get();
        while (timeMs > currentMax && !maxExecutionTime.compareAndSet(currentMax, timeMs)) {
            currentMax = maxExecutionTime.get();
        }
        
        long currentMin = minExecutionTime.get();
        while (timeMs < currentMin && !minExecutionTime.compareAndSet(currentMin, timeMs)) {
            currentMin = minExecutionTime.get();
        }
        
        // Update average
        updateAverageExecutionTime();
    }
    
    public void addResultCount(int count) {
        totalResultCount.addAndGet(count);
        updateAverageResultCount();
    }
    
    public void incrementCacheHits() {
        cacheHits.incrementAndGet();
    }
    
    public void updateLastExecution() {
        lastExecution.set(Instant.now());
    }
    
    private void updateAverageExecutionTime() {
        long execCount = executionCount.get();
        if (execCount > 0) {
            averageExecutionTime = (double) totalExecutionTime.get() / execCount;
        }
    }
    
    private void updateAverageResultCount() {
        long execCount = executionCount.get();
        if (execCount > 0) {
            averageResultCount = (double) totalResultCount.get() / execCount;
        }
    }
    
    // Getters for atomic values
    public long getExecutionCount() {
        return executionCount.get();
    }
    
    public long getTotalExecutions() {
        return executionCount.get();
    }
    
    public long getTotalExecutionTime() {
        return totalExecutionTime.get();
    }
    
    public long getMaxExecutionTime() {
        return maxExecutionTime.get();
    }
    
    public long getMinExecutionTime() {
        long min = minExecutionTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }
    
    public long getTotalResultCount() {
        return totalResultCount.get();
    }
    
    public long getCacheHits() {
        return cacheHits.get();
    }
    
    public Instant getLastExecution() {
        return lastExecution.get();
    }
    
    public double getAverageExecutionTime() {
        return averageExecutionTime;
    }
    
    public double getAverageResultCount() {
        return averageResultCount;
    }
    
    public double getCacheHitRate() {
        long total = executionCount.get();
        long hits = cacheHits.get();
        return total > 0 ? (double) hits / total * 100.0 : 0.0;
    }
}