package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for query patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternStatistics {
    
    private String patternId;
    private AtomicInteger executionCount;
    private AtomicLong totalExecutionTime;
    private AtomicLong minExecutionTime;
    private AtomicLong maxExecutionTime;
    private Instant firstSeen;
    private Instant lastSeen;
    private AtomicInteger successCount;
    private AtomicInteger failureCount;
    private double averageExecutionTime;
    private double standardDeviation;
    private AtomicLong totalRowsProcessed;
    
    /**
     * Initialize atomic fields if null
     */
    public PatternStatistics init() {
        if (executionCount == null) executionCount = new AtomicInteger(0);
        if (totalExecutionTime == null) totalExecutionTime = new AtomicLong(0);
        if (minExecutionTime == null) minExecutionTime = new AtomicLong(Long.MAX_VALUE);
        if (maxExecutionTime == null) maxExecutionTime = new AtomicLong(0);
        if (successCount == null) successCount = new AtomicInteger(0);
        if (failureCount == null) failureCount = new AtomicInteger(0);
        if (totalRowsProcessed == null) totalRowsProcessed = new AtomicLong(0);
        return this;
    }
    
    /**
     * Update statistics with new execution
     */
    public void updateWith(QueryExecution execution) {
        init();
        
        executionCount.incrementAndGet();
        totalExecutionTime.addAndGet(execution.getExecutionTimeMs());
        
        long execTime = execution.getExecutionTimeMs();
        minExecutionTime.updateAndGet(current -> Math.min(current, execTime));
        maxExecutionTime.updateAndGet(current -> Math.max(current, execTime));
        
        if (execution.isSuccessful()) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }
        
        if (execution.getRowsAffected() > 0) {
            totalRowsProcessed.addAndGet(execution.getRowsAffected());
        }
        
        lastSeen = Instant.now();
        if (firstSeen == null) {
            firstSeen = Instant.now();
        }
        
        // Recalculate average
        averageExecutionTime = totalExecutionTime.get() / (double) executionCount.get();
    }
    
    /**
     * Get success rate percentage
     */
    public double getSuccessRate() {
        int total = getExecutionCount().get();
        if (total == 0) return 0.0;
        return (successCount.get() / (double) total) * 100.0;
    }
    
    /**
     * Get failure rate percentage
     */
    public double getFailureRate() {
        return 100.0 - getSuccessRate();
    }
    
    /**
     * Get average rows processed per execution
     */
    public double getAverageRowsProcessed() {
        int execCount = getExecutionCount().get();
        if (execCount == 0) return 0.0;
        return totalRowsProcessed.get() / (double) execCount;
    }
    
    /**
     * Safe getters for atomic fields
     */
    public AtomicInteger getExecutionCount() {
        return executionCount != null ? executionCount : new AtomicInteger(0);
    }
    
    public AtomicLong getTotalExecutionTime() {
        return totalExecutionTime != null ? totalExecutionTime : new AtomicLong(0);
    }
    
    public AtomicLong getMinExecutionTime() {
        return minExecutionTime != null ? minExecutionTime : new AtomicLong(0);
    }
    
    public AtomicLong getMaxExecutionTime() {
        return maxExecutionTime != null ? maxExecutionTime : new AtomicLong(0);
    }
    
    public AtomicInteger getSuccessCount() {
        return successCount != null ? successCount : new AtomicInteger(0);
    }
    
    public AtomicInteger getFailureCount() {
        return failureCount != null ? failureCount : new AtomicInteger(0);
    }
    
    public AtomicLong getTotalRowsProcessed() {
        return totalRowsProcessed != null ? totalRowsProcessed : new AtomicLong(0);
    }
}