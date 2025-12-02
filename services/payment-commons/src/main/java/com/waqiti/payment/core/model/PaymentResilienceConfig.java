package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * Payment Resilience Configuration
 * 
 * Configuration parameters for payment resilience patterns including
 * circuit breakers, bulkheads, timeouts, and rate limiting.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResilienceConfig {
    
    /**
     * Circuit breaker failure threshold percentage (0-100)
     */
    private Integer circuitBreakerFailureThreshold;
    
    /**
     * Circuit breaker wait duration in seconds before allowing test calls
     */
    private Long circuitBreakerWaitDurationSeconds;
    
    /**
     * Minimum number of calls before circuit breaker can open
     */
    private Integer circuitBreakerMinimumCalls;
    
    /**
     * Time window for circuit breaker failure rate calculation
     */
    private Duration circuitBreakerSlidingWindowDuration;
    
    /**
     * Maximum concurrent calls allowed (bulkhead pattern)
     */
    private Integer bulkheadMaxConcurrent;
    
    /**
     * Maximum wait time for bulkhead permission
     */
    private Duration bulkheadMaxWaitTime;
    
    /**
     * Operation timeout duration
     */
    private Duration timeoutDuration;
    
    /**
     * Rate limit: requests per minute
     */
    private Integer rateLimitRequestsPerMinute;
    
    /**
     * Rate limit: requests per second
     */
    private Integer rateLimitRequestsPerSecond;
    
    /**
     * Rate limit time window
     */
    private Duration rateLimitTimeWindow;
    
    /**
     * Retry configuration
     */
    private RetryConfig retryConfig;
    
    /**
     * Whether to enable fallback mechanisms
     */
    @Builder.Default
    private Boolean enableFallback = true;
    
    /**
     * Fallback timeout duration
     */
    private Duration fallbackTimeout;
    
    /**
     * Health check interval
     */
    private Duration healthCheckInterval;
    
    /**
     * Whether to enable automatic recovery
     */
    @Builder.Default
    private Boolean enableAutoRecovery = true;
    
    /**
     * Retry configuration nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfig {
        private Integer maxAttempts;
        private Duration baseDelay;
        private Duration maxDelay;
        private Double backoffMultiplier;
        private Boolean enableJitter;
    }
    
    /**
     * Create default configuration
     */
    public static PaymentResilienceConfig createDefault() {
        return PaymentResilienceConfig.builder()
            .circuitBreakerFailureThreshold(50)
            .circuitBreakerWaitDurationSeconds(60L)
            .circuitBreakerMinimumCalls(10)
            .circuitBreakerSlidingWindowDuration(Duration.ofMinutes(2))
            .bulkheadMaxConcurrent(10)
            .bulkheadMaxWaitTime(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(30))
            .rateLimitRequestsPerMinute(60)
            .rateLimitRequestsPerSecond(10)
            .rateLimitTimeWindow(Duration.ofMinutes(1))
            .enableFallback(true)
            .fallbackTimeout(Duration.ofSeconds(5))
            .healthCheckInterval(Duration.ofSeconds(30))
            .enableAutoRecovery(true)
            .retryConfig(RetryConfig.builder()
                .maxAttempts(3)
                .baseDelay(Duration.ofSeconds(1))
                .maxDelay(Duration.ofMinutes(1))
                .backoffMultiplier(2.0)
                .enableJitter(true)
                .build())
            .build();
    }
    
    /**
     * Create high throughput configuration
     */
    public static PaymentResilienceConfig createHighThroughput() {
        return PaymentResilienceConfig.builder()
            .circuitBreakerFailureThreshold(60)
            .circuitBreakerWaitDurationSeconds(30L)
            .circuitBreakerMinimumCalls(20)
            .bulkheadMaxConcurrent(50)
            .bulkheadMaxWaitTime(Duration.ofMillis(500))
            .timeoutDuration(Duration.ofSeconds(10))
            .rateLimitRequestsPerMinute(300)
            .rateLimitRequestsPerSecond(20)
            .enableFallback(true)
            .enableAutoRecovery(true)
            .build();
    }
    
    /**
     * Create conservative configuration
     */
    public static PaymentResilienceConfig createConservative() {
        return PaymentResilienceConfig.builder()
            .circuitBreakerFailureThreshold(30)
            .circuitBreakerWaitDurationSeconds(120L)
            .circuitBreakerMinimumCalls(5)
            .bulkheadMaxConcurrent(5)
            .bulkheadMaxWaitTime(Duration.ofSeconds(3))
            .timeoutDuration(Duration.ofMinutes(1))
            .rateLimitRequestsPerMinute(30)
            .rateLimitRequestsPerSecond(2)
            .enableFallback(true)
            .enableAutoRecovery(false)
            .build();
    }
    
    /**
     * Validate configuration parameters
     */
    public boolean isValid() {
        return circuitBreakerFailureThreshold != null && 
               circuitBreakerFailureThreshold >= 0 && 
               circuitBreakerFailureThreshold <= 100 &&
               circuitBreakerWaitDurationSeconds != null && 
               circuitBreakerWaitDurationSeconds > 0 &&
               bulkheadMaxConcurrent != null && 
               bulkheadMaxConcurrent > 0 &&
               timeoutDuration != null && 
               !timeoutDuration.isNegative();
    }
}