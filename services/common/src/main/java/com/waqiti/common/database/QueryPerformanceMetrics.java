package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Query performance metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryPerformanceMetrics {
    private String queryId;
    private String queryPattern;
    private long executionTimeMs;
    private double averageExecutionTimeMs;
    private long rowsProcessed;
    private long bytesRead;
    private long bytesWritten;
    private Instant timestamp;
    private Instant lastExecuted;
    private String connectionPool;
    private double cpuUsage;
    private double memoryUsage;
    private boolean optimized;
    
    // Additional fields needed by QueryPredictionService
    private int executionCount;
    private int successCount;
    private long peakConcurrentQueries;
    private double standardDeviation;
    
    public long getExecutionTime() {
        return executionTimeMs;
    }
    
    public double getAverageExecutionTimeMs() {
        return averageExecutionTimeMs > 0 ? averageExecutionTimeMs : executionTimeMs;
    }
    
    public int getExecutionCount() {
        return executionCount;
    }
    
    public int getSuccessCount() {
        return successCount;
    }
    
    public long getPeakConcurrentQueries() {
        return peakConcurrentQueries;
    }
    
    public void setExecutionCount(int executionCount) {
        this.executionCount = executionCount;
    }
    
    public double getAverageExecutionTime() {
        return getAverageExecutionTimeMs();
    }
    
    public void addExecution(long executionTime, int rowsProcessed) {
        this.executionCount++;
        this.executionTimeMs = executionTime;
        this.rowsProcessed = rowsProcessed;
        // Update average
        if (this.averageExecutionTimeMs == 0) {
            this.averageExecutionTimeMs = executionTime;
        } else {
            this.averageExecutionTimeMs = ((this.averageExecutionTimeMs * (executionCount - 1)) + executionTime) / executionCount;
        }
        this.lastExecuted = Instant.now();
    }
    
    public void addError(long executionTime, Exception error) {
        this.executionCount++;
        this.executionTimeMs = executionTime;
        // Don't count as success
        this.lastExecuted = Instant.now();
    }
    
    public long getSlowQueryCount(long threshold) {
        // Simple implementation - in reality would track this
        return averageExecutionTimeMs > threshold ? executionCount : 0;
    }
    
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }
    
    public void setAverageExecutionTimeMs(double averageExecutionTimeMs) {
        this.averageExecutionTimeMs = averageExecutionTimeMs;
    }
    
    public void setLastExecuted(Instant lastExecuted) {
        this.lastExecuted = lastExecuted;
    }
    
    public static QueryPerformanceMetrics fromExecution(QueryExecution execution) {
        return QueryPerformanceMetrics.builder()
            .queryId(execution.getQueryId())
            .queryPattern(execution.getQueryText())
            .executionTimeMs(execution.getExecutionTimeMs())
            .averageExecutionTimeMs(execution.getExecutionTimeMs())
            .timestamp(execution.getStartTime())
            .lastExecuted(execution.getEndTime())
            .executionCount(1)
            .successCount(1)
            .optimized(execution.isOptimized())
            .build();
    }
}