package com.waqiti.common.database.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Overall database performance statistics DTO.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverallStats {
    
    private long totalQueries;
    private long slowQueries;
    private double cacheHitRate;
    private double averageExecutionTime;
    private long deadlockCount;
    private double queryThroughput; // queries per second
    private double errorRate;
    private int activeConnections;
    private double cpuUtilization;
    private double memoryUtilization;
    private long diskIOPS;
    
    /**
     * Calculate overall performance score (0-100).
     *
     * @return performance score
     */
    public double getPerformanceScore() {
        double score = 100.0;
        
        // Penalize for slow queries
        if (totalQueries > 0) {
            double slowQueryRatio = (double) slowQueries / totalQueries;
            score -= slowQueryRatio * 30; // Up to 30 points deduction
        }
        
        // Reward good cache hit rate
        if (cacheHitRate < 80) {
            score -= (80 - cacheHitRate) * 0.5;
        }
        
        // Penalize high average execution time
        if (averageExecutionTime > 100) {
            score -= Math.min(20, (averageExecutionTime - 100) / 50);
        }
        
        // Penalize deadlocks
        score -= Math.min(15, deadlockCount * 2);
        
        // Penalize high error rate
        score -= errorRate * 10;
        
        // Penalize resource utilization above 80%
        if (cpuUtilization > 80) {
            score -= (cpuUtilization - 80) * 0.5;
        }
        
        if (memoryUtilization > 80) {
            score -= (memoryUtilization - 80) * 0.5;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Get performance grade based on score.
     *
     * @return letter grade (A, B, C, D, F)
     */
    public String getPerformanceGrade() {
        double score = getPerformanceScore();
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
    
    /**
     * Calculate slow query percentage.
     *
     * @return percentage of slow queries
     */
    public double getSlowQueryPercentage() {
        if (totalQueries == 0) {
            return 0.0;
        }
        return (double) slowQueries / totalQueries * 100.0;
    }
    
    /**
     * Check if performance is acceptable.
     *
     * @return true if performance metrics are within acceptable ranges
     */
    public boolean isPerformanceAcceptable() {
        return getSlowQueryPercentage() < 5.0 && 
               getPerformanceScore() > 70.0 &&
               cacheHitRate > 60.0 &&
               averageExecutionTime < 500;
    }
    
    /**
     * Get resource utilization status.
     *
     * @return resource utilization status
     */
    public String getResourceUtilizationStatus() {
        if (cpuUtilization > 90 || memoryUtilization > 90) {
            return "CRITICAL";
        } else if (cpuUtilization > 80 || memoryUtilization > 80) {
            return "HIGH";
        } else if (cpuUtilization > 60 || memoryUtilization > 60) {
            return "MODERATE";
        } else {
            return "LOW";
        }
    }
}