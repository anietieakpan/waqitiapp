package com.waqiti.common.database.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive report on slow queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowQueryReport {
    
    // Individual slow query fields expected by DatabasePerformanceMonitoringService
    private String query;
    private double averageExecutionTime;
    private long executionCount;
    private double maxExecutionTime;
    
    // Original comprehensive report fields
    private Instant reportGeneratedAt;
    private Duration reportPeriod;
    private int totalQueriesAnalyzed;
    private int slowQueriesFound;
    private List<SlowQuery> topSlowQueries;
    private Map<String, Integer> slowQueriesByTable;
    private Map<String, Integer> slowQueriesByType;
    private Map<SlowQuery.QuerySeverity, Integer> slowQueriesBySeverity;
    private Duration averageSlowQueryTime;
    private Duration maxSlowQueryTime;
    private SlowQuery slowestQuery;
    private List<String> recommendedOptimizations;
    private double overallPerformanceScore;
    private Map<String, Object> additionalMetrics;
    
    /**
     * Calculate slow query percentage
     */
    public double getSlowQueryPercentage() {
        if (totalQueriesAnalyzed == 0) {
            return 0.0;
        }
        return (double) slowQueriesFound / totalQueriesAnalyzed * 100.0;
    }
    
    /**
     * Check if performance is acceptable
     */
    public boolean isPerformanceAcceptable() {
        return getSlowQueryPercentage() < 5.0 && overallPerformanceScore > 7.0;
    }
    
    /**
     * Get performance status
     */
    public PerformanceStatus getPerformanceStatus() {
        if (overallPerformanceScore >= 9.0) {
            return PerformanceStatus.EXCELLENT;
        } else if (overallPerformanceScore >= 7.0) {
            return PerformanceStatus.GOOD;
        } else if (overallPerformanceScore >= 5.0) {
            return PerformanceStatus.FAIR;
        } else {
            return PerformanceStatus.POOR;
        }
    }
    
    /**
     * Get critical issues count
     */
    public int getCriticalIssuesCount() {
        return slowQueriesBySeverity.getOrDefault(SlowQuery.QuerySeverity.CRITICAL, 0);
    }
    
    /**
     * Get high priority issues count
     */
    public int getHighPriorityIssuesCount() {
        return slowQueriesBySeverity.getOrDefault(SlowQuery.QuerySeverity.HIGH, 0);
    }
    
    /**
     * Check if immediate attention is required
     */
    public boolean requiresImmediateAttention() {
        return getCriticalIssuesCount() > 0 || 
               getHighPriorityIssuesCount() > 10 ||
               getSlowQueryPercentage() > 10.0;
    }
    
    public enum PerformanceStatus {
        EXCELLENT, GOOD, FAIR, POOR
    }
}