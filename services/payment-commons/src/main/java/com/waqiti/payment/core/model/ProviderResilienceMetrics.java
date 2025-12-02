package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provider Resilience Metrics
 * 
 * Comprehensive resilience metrics for a payment provider including
 * circuit breaker, bulkhead, and other resilience pattern metrics.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResilienceMetrics {
    
    /**
     * Provider identifier
     */
    private String provider;
    
    /**
     * Circuit breaker metrics
     */
    private CircuitBreakerMetrics circuitBreakerMetrics;
    
    /**
     * Bulkhead metrics
     */
    private BulkheadMetrics bulkheadMetrics;
    
    /**
     * Rate limiting metrics
     */
    private RateLimitMetrics rateLimitMetrics;
    
    /**
     * Timeout metrics
     */
    private TimeoutMetrics timeoutMetrics;
    
    /**
     * Retry metrics
     */
    private RetryMetrics retryMetrics;
    
    /**
     * When these metrics were collected
     */
    private LocalDateTime timestamp;
    
    /**
     * Time period these metrics cover
     */
    private String timePeriod;
    
    /**
     * Overall resilience score (0-100)
     */
    private Integer resilienceScore;
    
    /**
     * Rate limiting metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitMetrics {
        private Long totalRequests;
        private Long allowedRequests;
        private Long rejectedRequests;
        private Float rejectionRate;
        private Long currentTokens;
        private Long maxTokens;
        private String windowType;
        private Long windowSizeMs;
    }
    
    /**
     * Timeout metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeoutMetrics {
        private Long totalCalls;
        private Long timeoutCalls;
        private Float timeoutRate;
        private Long averageExecutionTimeMs;
        private Long maxExecutionTimeMs;
        private Long configuredTimeoutMs;
    }
    
    /**
     * Retry metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryMetrics {
        private Long totalRetries;
        private Long successfulRetries;
        private Long failedRetries;
        private Float retrySuccessRate;
        private Integer maxAttempts;
        private Long averageRetryDelayMs;
    }
    
    /**
     * Calculate overall provider health from resilience metrics
     */
    public ProviderHealthLevel getHealthLevel() {
        if (circuitBreakerMetrics == null) {
            return ProviderHealthLevel.UNKNOWN;
        }
        
        String cbState = circuitBreakerMetrics.getState();
        Float failureRate = circuitBreakerMetrics.getFailureRate();
        
        if ("OPEN".equals(cbState)) {
            return ProviderHealthLevel.CRITICAL;
        } else if ("HALF_OPEN".equals(cbState)) {
            return ProviderHealthLevel.DEGRADED;
        } else if (failureRate != null && failureRate > 20) {
            return ProviderHealthLevel.DEGRADED;
        } else if (failureRate != null && failureRate > 10) {
            return ProviderHealthLevel.WARNING;
        } else {
            return ProviderHealthLevel.HEALTHY;
        }
    }
    
    /**
     * Check if provider is experiencing high error rates
     */
    public boolean hasHighErrorRate() {
        return circuitBreakerMetrics != null && 
               circuitBreakerMetrics.getFailureRate() != null &&
               circuitBreakerMetrics.getFailureRate() > 15.0f;
    }
    
    /**
     * Check if provider is under high load
     */
    public boolean isUnderHighLoad() {
        if (bulkheadMetrics == null) {
            return false;
        }
        
        int available = bulkheadMetrics.getAvailablePermissions();
        int max = bulkheadMetrics.getMaxPermissions();
        
        return available < (max * 0.2); // Less than 20% capacity available
    }
    
    /**
     * Check if rate limiting is active
     */
    public boolean isRateLimitActive() {
        return rateLimitMetrics != null &&
               rateLimitMetrics.getRejectedRequests() != null &&
               rateLimitMetrics.getRejectedRequests() > 0;
    }
    
    /**
     * Get capacity utilization percentage
     */
    public float getCapacityUtilization() {
        if (bulkheadMetrics == null) {
            return 0.0f;
        }
        
        int available = bulkheadMetrics.getAvailablePermissions();
        int max = bulkheadMetrics.getMaxPermissions();
        
        return ((float)(max - available) / max) * 100;
    }
    
    /**
     * Calculate resilience effectiveness score
     */
    public int calculateResilienceScore() {
        int score = 100;
        
        // Circuit breaker impact
        if (circuitBreakerMetrics != null) {
            if ("OPEN".equals(circuitBreakerMetrics.getState())) {
                score -= 40;
            } else if ("HALF_OPEN".equals(circuitBreakerMetrics.getState())) {
                score -= 20;
            }
            
            if (circuitBreakerMetrics.getFailureRate() != null) {
                score -= Math.min(30, (int)(circuitBreakerMetrics.getFailureRate() / 2));
            }
        }
        
        // Bulkhead impact
        if (isUnderHighLoad()) {
            score -= 15;
        }
        
        // Rate limiting impact
        if (rateLimitMetrics != null && rateLimitMetrics.getRejectionRate() != null) {
            score -= Math.min(15, (int)(rateLimitMetrics.getRejectionRate() / 2));
        }
        
        // Timeout impact
        if (timeoutMetrics != null && timeoutMetrics.getTimeoutRate() != null) {
            score -= Math.min(20, (int)(timeoutMetrics.getTimeoutRate() * 2));
        }
        
        return Math.max(0, score);
    }
    
    public enum ProviderHealthLevel {
        HEALTHY,
        WARNING,
        DEGRADED,
        CRITICAL,
        UNKNOWN
    }
}