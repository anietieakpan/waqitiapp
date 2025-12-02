package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provider Performance Metrics
 * 
 * Performance and reliability metrics for a specific payment provider,
 * including success rates, response times, and availability data.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPerformanceMetrics {
    
    /**
     * Provider identifier
     */
    private String provider;
    
    /**
     * Success rate percentage (0-100)
     */
    private Double successRate;
    
    /**
     * Average response time in milliseconds
     */
    private Double averageResponseTime;
    
    /**
     * Total number of transactions processed
     */
    private Long totalTransactions;
    
    /**
     * Number of successful transactions
     */
    private Long successfulTransactions;
    
    /**
     * Number of failed transactions
     */
    private Long failedTransactions;
    
    /**
     * Provider availability percentage (0-100)
     */
    private Double availability;
    
    /**
     * Peak response time recorded
     */
    private Double peakResponseTime;
    
    /**
     * Minimum response time recorded
     */
    private Double minResponseTime;
    
    /**
     * 95th percentile response time
     */
    private Double p95ResponseTime;
    
    /**
     * 99th percentile response time
     */
    private Double p99ResponseTime;
    
    /**
     * Number of timeouts
     */
    private Long timeoutCount;
    
    /**
     * Number of circuit breaker activations
     */
    private Long circuitBreakerActivations;
    
    /**
     * Average transaction amount
     */
    private Double averageTransactionAmount;
    
    /**
     * Total volume processed
     */
    private Double totalVolume;
    
    /**
     * Error rate percentage (0-100)
     */
    private Double errorRate;
    
    /**
     * Retry rate percentage (0-100)
     */
    private Double retryRate;
    
    /**
     * Current health status
     */
    private ProviderHealthStatus healthStatus;
    
    /**
     * Timestamp of these metrics
     */
    private LocalDateTime timestamp;
    
    /**
     * Time period these metrics cover
     */
    private String timePeriod;
    
    /**
     * Calculate failure rate
     */
    public double getFailureRate() {
        if (totalTransactions == null || totalTransactions == 0) {
            return 0.0;
        }
        
        long failed = failedTransactions != null ? failedTransactions : 0L;
        return (failed / (double) totalTransactions) * 100.0;
    }
    
    /**
     * Check if provider performance is acceptable
     */
    public boolean isPerformanceAcceptable() {
        return successRate != null && successRate >= 95.0 &&
               averageResponseTime != null && averageResponseTime <= 5000.0 &&
               availability != null && availability >= 99.0;
    }
    
    /**
     * Get performance grade
     */
    public PerformanceGrade getPerformanceGrade() {
        if (successRate == null || averageResponseTime == null) {
            return PerformanceGrade.UNKNOWN;
        }
        
        // Grade based on success rate and response time
        if (successRate >= 99.5 && averageResponseTime <= 1000) {
            return PerformanceGrade.A_PLUS;
        } else if (successRate >= 99.0 && averageResponseTime <= 2000) {
            return PerformanceGrade.A;
        } else if (successRate >= 98.0 && averageResponseTime <= 3000) {
            return PerformanceGrade.B;
        } else if (successRate >= 95.0 && averageResponseTime <= 5000) {
            return PerformanceGrade.C;
        } else {
            return PerformanceGrade.D;
        }
    }
    
    /**
     * Check if provider needs attention
     */
    public boolean needsAttention() {
        return successRate != null && successRate < 95.0 ||
               averageResponseTime != null && averageResponseTime > 5000.0 ||
               availability != null && availability < 99.0 ||
               errorRate != null && errorRate > 5.0;
    }
    
    /**
     * Get throughput (transactions per minute)
     */
    public double getThroughputPerMinute() {
        // This would typically be calculated based on the time period
        // For now, returning a simple calculation
        if (totalTransactions == null) {
            return 0.0;
        }
        
        // Assuming metrics are for last hour, calculate per minute
        return totalTransactions / 60.0;
    }
    
    public enum PerformanceGrade {
        A_PLUS,  // Excellent
        A,       // Very Good
        B,       // Good
        C,       // Acceptable
        D,       // Poor
        UNKNOWN  // Insufficient data
    }
    
    public enum ProviderHealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
}