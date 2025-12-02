package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provider Health Status
 * 
 * Health status information for a payment provider including
 * circuit breaker state, bulkhead capacity, and rate limiting status.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderHealthStatus {
    
    /**
     * Provider identifier
     */
    private String provider;
    
    /**
     * Overall health indicator
     */
    private Boolean healthy;
    
    /**
     * Circuit breaker state (CLOSED, OPEN, HALF_OPEN)
     */
    private String circuitBreakerState;
    
    /**
     * Circuit breaker failure rate percentage
     */
    private Float circuitBreakerFailureRate;
    
    /**
     * Circuit breaker success rate percentage
     */
    private Float circuitBreakerSuccessRate;
    
    /**
     * Available bulkhead permissions
     */
    private Integer bulkheadAvailablePermissions;
    
    /**
     * Maximum bulkhead permissions
     */
    private Integer bulkheadMaxPermissions;
    
    /**
     * Rate limiting status
     */
    private Boolean rateLimitOk;
    
    /**
     * Current rate limit utilization percentage
     */
    private Float rateLimitUtilization;
    
    /**
     * Average response time in milliseconds
     */
    private Long averageResponseTimeMs;
    
    /**
     * Last successful call timestamp
     */
    private LocalDateTime lastSuccessfulCall;
    
    /**
     * Last failed call timestamp
     */
    private LocalDateTime lastFailedCall;
    
    /**
     * Number of consecutive failures
     */
    private Integer consecutiveFailures;
    
    /**
     * Provider availability percentage (0-100)
     */
    private Float availabilityPercentage;
    
    /**
     * Current load level
     */
    private LoadLevel loadLevel;
    
    /**
     * Health check timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * Time since last health check in milliseconds
     */
    private Long timeSinceLastCheckMs;
    
    /**
     * Additional health metrics
     */
    private HealthMetrics healthMetrics;
    
    /**
     * Load level enumeration
     */
    public enum LoadLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
        UNKNOWN
    }
    
    /**
     * Additional health metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthMetrics {
        private Long totalRequests;
        private Long successfulRequests;
        private Long failedRequests;
        private Long timeoutRequests;
        private Double throughputPerSecond;
        private Long errorCount;
        private String lastErrorMessage;
    }
    
    /**
     * Check if provider is available for new requests
     */
    public boolean isAvailable() {
        return healthy != null && healthy &&
               !"OPEN".equals(circuitBreakerState) &&
               (bulkheadAvailablePermissions == null || bulkheadAvailablePermissions > 0) &&
               (rateLimitOk == null || rateLimitOk);
    }
    
    /**
     * Check if provider is degraded but still usable
     */
    public boolean isDegraded() {
        return healthy != null && healthy &&
               (circuitBreakerFailureRate != null && circuitBreakerFailureRate > 10) ||
               (averageResponseTimeMs != null && averageResponseTimeMs > 5000) ||
               (availabilityPercentage != null && availabilityPercentage < 99);
    }
    
    /**
     * Check if provider is in critical state
     */
    public boolean isCritical() {
        return !isAvailable() ||
               "OPEN".equals(circuitBreakerState) ||
               (consecutiveFailures != null && consecutiveFailures > 5) ||
               (availabilityPercentage != null && availabilityPercentage < 95);
    }
    
    /**
     * Get health score (0-100)
     */
    public int getHealthScore() {
        if (!healthy) {
            return 0;
        }
        
        int score = 100;
        
        // Deduct for circuit breaker issues
        if ("OPEN".equals(circuitBreakerState)) {
            score -= 50;
        } else if ("HALF_OPEN".equals(circuitBreakerState)) {
            score -= 20;
        }
        
        // Deduct for high failure rate
        if (circuitBreakerFailureRate != null) {
            score -= Math.min(30, (int) (circuitBreakerFailureRate / 2));
        }
        
        // Deduct for bulkhead saturation
        if (bulkheadAvailablePermissions != null && bulkheadMaxPermissions != null) {
            float utilizationPercent = (1.0f - (float) bulkheadAvailablePermissions / bulkheadMaxPermissions) * 100;
            if (utilizationPercent > 80) {
                score -= 15;
            }
        }
        
        // Deduct for rate limiting
        if (rateLimitOk != null && !rateLimitOk) {
            score -= 10;
        }
        
        // Deduct for poor availability
        if (availabilityPercentage != null) {
            if (availabilityPercentage < 99) {
                score -= (99 - availabilityPercentage.intValue());
            }
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Get provider capacity utilization
     */
    public float getCapacityUtilization() {
        if (bulkheadAvailablePermissions == null || bulkheadMaxPermissions == null) {
            return 0.0f;
        }
        
        return (1.0f - (float) bulkheadAvailablePermissions / bulkheadMaxPermissions) * 100;
    }
    
    /**
     * Get recommended action based on health status
     */
    public String getRecommendedAction() {
        if (isCritical()) {
            return "IMMEDIATE_ATTENTION_REQUIRED";
        } else if (isDegraded()) {
            return "MONITOR_CLOSELY";
        } else if (isAvailable()) {
            return "NORMAL_OPERATION";
        } else {
            return "INVESTIGATE";
        }
    }
    
    /**
     * Create healthy status
     */
    public static ProviderHealthStatus createHealthy(String provider) {
        return ProviderHealthStatus.builder()
            .provider(provider)
            .healthy(true)
            .circuitBreakerState("CLOSED")
            .circuitBreakerFailureRate(0.0f)
            .circuitBreakerSuccessRate(100.0f)
            .rateLimitOk(true)
            .availabilityPercentage(100.0f)
            .loadLevel(LoadLevel.LOW)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create unhealthy status
     */
    public static ProviderHealthStatus createUnhealthy(String provider, String reason) {
        return ProviderHealthStatus.builder()
            .provider(provider)
            .healthy(false)
            .circuitBreakerState("OPEN")
            .availabilityPercentage(0.0f)
            .timestamp(LocalDateTime.now())
            .healthMetrics(HealthMetrics.builder()
                .lastErrorMessage(reason)
                .build())
            .build();
    }
}