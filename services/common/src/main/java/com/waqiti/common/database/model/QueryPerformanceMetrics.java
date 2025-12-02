package com.waqiti.common.database.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Data
@Builder
@AllArgsConstructor
public class QueryPerformanceMetrics {
    @Builder.Default
    private String queryName = "";
    @Builder.Default
    private AtomicLong executionCount = new AtomicLong(0);
    @Builder.Default
    private AtomicLong totalExecutionTime = new AtomicLong(0);
    @Builder.Default
    private AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
    @Builder.Default
    private AtomicLong maxExecutionTime = new AtomicLong(0);
    @Builder.Default
    private AtomicLong errorCount = new AtomicLong(0);
    private LocalDateTime firstExecution;
    private LocalDateTime lastExecution;
    
    public double getAverageExecutionTime() {
        long count = executionCount.get();
        return count > 0 ? (double) totalExecutionTime.get() / count : 0.0;
    }
    
    public void addExecution(long executionTime, int resultCount) {
        if (firstExecution == null) {
            firstExecution = LocalDateTime.now();
        }
        lastExecution = LocalDateTime.now();
        
        executionCount.incrementAndGet();
        totalExecutionTime.addAndGet(executionTime);
        
        // Update min/max execution times
        minExecutionTime.updateAndGet(min -> Math.min(min, executionTime));
        maxExecutionTime.updateAndGet(max -> Math.max(max, executionTime));
    }
    
    public void addError(long executionTime, Exception error) {
        errorCount.incrementAndGet();
        addExecution(executionTime, 0);
    }
    
    public long getSlowQueryCount(long slowQueryThreshold) {
        // This is a simplified implementation
        // In reality, we'd track individual query times
        return getAverageExecutionTime() > slowQueryThreshold ? executionCount.get() : 0;
    }
}