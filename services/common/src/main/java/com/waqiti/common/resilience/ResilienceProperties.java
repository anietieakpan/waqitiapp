package com.waqiti.common.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for resilience patterns (circuit breakers, retries, etc.).
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "waqiti.resilience")
public class ResilienceProperties {
    
    /**
     * Circuit breaker configurations by name
     */
    private Map<String, CircuitBreakerConfig> circuitBreakers = new HashMap<>();
    
    /**
     * Retry configurations by name
     */
    private Map<String, RetryConfig> retries = new HashMap<>();
    
    /**
     * Bulkhead configurations by name
     */
    private Map<String, BulkheadConfig> bulkheads = new HashMap<>();
    
    /**
     * Rate limiter configurations by name
     */
    private Map<String, RateLimiterConfig> rateLimiters = new HashMap<>();
    
    public ResilienceProperties() {
        // Initialize with default configurations
        initializeDefaults();
    }
    
    private void initializeDefaults() {
        // Default circuit breaker for external API calls
        circuitBreakers.put("default", new CircuitBreakerConfig());
        circuitBreakers.put("payment-api", new CircuitBreakerConfig(60.0f, 10000L, 10, 5, 70.0f, 2000L));
        circuitBreakers.put("bank-api", new CircuitBreakerConfig(50.0f, 15000L, 20, 10, 80.0f, 3000L));
        circuitBreakers.put("notification-api", new CircuitBreakerConfig(70.0f, 5000L, 5, 3, 60.0f, 1000L));
        
        // Default retry configurations
        retries.put("default", new RetryConfig());
        retries.put("payment-retry", new RetryConfig(3, 1000L, 5000L, true, List.of()));
        retries.put("bank-retry", new RetryConfig(5, 2000L, 10000L, true, List.of()));
        retries.put("notification-retry", new RetryConfig(2, 500L, 2000L, false, List.of()));
        
        // Default bulkhead configurations
        bulkheads.put("default", new BulkheadConfig());
        bulkheads.put("payment-bulkhead", new BulkheadConfig(10, 1000L));
        bulkheads.put("bank-bulkhead", new BulkheadConfig(5, 2000L));
        bulkheads.put("notification-bulkhead", new BulkheadConfig(20, 500L));
        
        // Default rate limiter configurations
        rateLimiters.put("default", new RateLimiterConfig());
        rateLimiters.put("payment-limiter", new RateLimiterConfig(10, 1000L, 3000L));
        rateLimiters.put("bank-limiter", new RateLimiterConfig(5, 2000L, 5000L));
        rateLimiters.put("notification-limiter", new RateLimiterConfig(50, 1000L, 1000L));
    }
    
    // Getters and setters
    public Map<String, CircuitBreakerConfig> getCircuitBreakers() { return circuitBreakers; }
    public void setCircuitBreakers(Map<String, CircuitBreakerConfig> circuitBreakers) { this.circuitBreakers = circuitBreakers; }
    
    public Map<String, RetryConfig> getRetries() { return retries; }
    public void setRetries(Map<String, RetryConfig> retries) { this.retries = retries; }
    
    public Map<String, BulkheadConfig> getBulkheads() { return bulkheads; }
    public void setBulkheads(Map<String, BulkheadConfig> bulkheads) { this.bulkheads = bulkheads; }
    
    public Map<String, RateLimiterConfig> getRateLimiters() { return rateLimiters; }
    public void setRateLimiters(Map<String, RateLimiterConfig> rateLimiters) { this.rateLimiters = rateLimiters; }
    
    /**
     * Circuit breaker configuration.
     */
    public static class CircuitBreakerConfig {
        
        @Min(value = 1, message = "Failure rate threshold must be at least 1")
        @Max(value = 100, message = "Failure rate threshold must be at most 100")
        private float failureRateThreshold = 50.0f;
        
        @Min(value = 1000, message = "Wait duration must be at least 1000ms")
        private long waitDurationInOpenState = 60000L; // 1 minute
        
        @Min(value = 1, message = "Sliding window size must be at least 1")
        private int slidingWindowSize = 10;
        
        @Min(value = 1, message = "Minimum number of calls must be at least 1")
        private int minimumNumberOfCalls = 5;
        
        @Min(value = 1, message = "Slow call rate threshold must be at least 1")
        @Max(value = 100, message = "Slow call rate threshold must be at most 100")
        private float slowCallRateThreshold = 100.0f;
        
        @Min(value = 1000, message = "Slow call duration threshold must be at least 1000ms")
        private long slowCallDurationThreshold = 60000L; // 1 minute
        
        public CircuitBreakerConfig() {}
        
        public CircuitBreakerConfig(float failureRateThreshold, long waitDurationInOpenState, 
                                  int slidingWindowSize, int minimumNumberOfCalls,
                                  float slowCallRateThreshold, long slowCallDurationThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            this.waitDurationInOpenState = waitDurationInOpenState;
            this.slidingWindowSize = slidingWindowSize;
            this.minimumNumberOfCalls = minimumNumberOfCalls;
            this.slowCallRateThreshold = slowCallRateThreshold;
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }
        
        // Getters and setters
        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        
        public long getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(long waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }
        
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
        
        public float getSlowCallRateThreshold() { return slowCallRateThreshold; }
        public void setSlowCallRateThreshold(float slowCallRateThreshold) { this.slowCallRateThreshold = slowCallRateThreshold; }
        
        public long getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public void setSlowCallDurationThreshold(long slowCallDurationThreshold) { this.slowCallDurationThreshold = slowCallDurationThreshold; }
        
        @Override
        public String toString() {
            return String.format("CircuitBreakerConfig{failureRate=%.1f%%, waitDuration=%dms, windowSize=%d, minCalls=%d}",
                    failureRateThreshold, waitDurationInOpenState, slidingWindowSize, minimumNumberOfCalls);
        }
    }
    
    /**
     * Retry configuration.
     */
    public static class RetryConfig {
        
        @Min(value = 1, message = "Max attempts must be at least 1")
        private int maxAttempts = 3;
        
        @Min(value = 100, message = "Wait duration must be at least 100ms")
        private long waitDuration = 1000L;
        
        @Min(value = 100, message = "Max wait duration must be at least 100ms")
        private long maxWaitDuration = 10000L;
        
        @NotNull
        private boolean exponentialBackoff = true;
        
        @NotNull
        private List<Class<? extends Throwable>> retryExceptions = List.of(
                RuntimeException.class,
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                java.io.IOException.class
        );
        
        public RetryConfig() {}
        
        public RetryConfig(int maxAttempts, long waitDuration, long maxWaitDuration, 
                         boolean exponentialBackoff, List<Class<? extends Throwable>> retryExceptions) {
            this.maxAttempts = maxAttempts;
            this.waitDuration = waitDuration;
            this.maxWaitDuration = maxWaitDuration;
            this.exponentialBackoff = exponentialBackoff;
            this.retryExceptions = retryExceptions.isEmpty() ? this.retryExceptions : retryExceptions;
        }
        
        // Getters and setters
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        
        public long getWaitDuration() { return waitDuration; }
        public void setWaitDuration(long waitDuration) { this.waitDuration = waitDuration; }
        
        public long getMaxWaitDuration() { return maxWaitDuration; }
        public void setMaxWaitDuration(long maxWaitDuration) { this.maxWaitDuration = maxWaitDuration; }
        
        public boolean isExponentialBackoff() { return exponentialBackoff; }
        public void setExponentialBackoff(boolean exponentialBackoff) { this.exponentialBackoff = exponentialBackoff; }
        
        public List<Class<? extends Throwable>> getRetryExceptions() { return retryExceptions; }
        public void setRetryExceptions(List<Class<? extends Throwable>> retryExceptions) { this.retryExceptions = retryExceptions; }
        
        @Override
        public String toString() {
            return String.format("RetryConfig{maxAttempts=%d, waitDuration=%dms, exponentialBackoff=%s}",
                    maxAttempts, waitDuration, exponentialBackoff);
        }
    }
    
    /**
     * Bulkhead configuration.
     */
    public static class BulkheadConfig {
        
        @Min(value = 1, message = "Max concurrent calls must be at least 1")
        private int maxConcurrentCalls = 10;
        
        @Min(value = 100, message = "Max wait duration must be at least 100ms")
        private long maxWaitDuration = 1000L;
        
        public BulkheadConfig() {}
        
        public BulkheadConfig(int maxConcurrentCalls, long maxWaitDuration) {
            this.maxConcurrentCalls = maxConcurrentCalls;
            this.maxWaitDuration = maxWaitDuration;
        }
        
        // Getters and setters
        public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
        public void setMaxConcurrentCalls(int maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }
        
        public long getMaxWaitDuration() { return maxWaitDuration; }
        public void setMaxWaitDuration(long maxWaitDuration) { this.maxWaitDuration = maxWaitDuration; }
        
        @Override
        public String toString() {
            return String.format("BulkheadConfig{maxConcurrentCalls=%d, maxWaitDuration=%dms}",
                    maxConcurrentCalls, maxWaitDuration);
        }
    }
    
    /**
     * Rate limiter configuration.
     */
    public static class RateLimiterConfig {
        
        @Min(value = 1, message = "Limit for period must be at least 1")
        private int limitForPeriod = 10;
        
        @Min(value = 100, message = "Limit refresh period must be at least 100ms")
        private long limitRefreshPeriod = 1000L; // 1 second
        
        @Min(value = 100, message = "Timeout duration must be at least 100ms")
        private long timeoutDuration = 3000L; // 3 seconds
        
        public RateLimiterConfig() {}
        
        public RateLimiterConfig(int limitForPeriod, long limitRefreshPeriod, long timeoutDuration) {
            this.limitForPeriod = limitForPeriod;
            this.limitRefreshPeriod = limitRefreshPeriod;
            this.timeoutDuration = timeoutDuration;
        }
        
        // Getters and setters
        public int getLimitForPeriod() { return limitForPeriod; }
        public void setLimitForPeriod(int limitForPeriod) { this.limitForPeriod = limitForPeriod; }
        
        public long getLimitRefreshPeriod() { return limitRefreshPeriod; }
        public void setLimitRefreshPeriod(long limitRefreshPeriod) { this.limitRefreshPeriod = limitRefreshPeriod; }
        
        public long getTimeoutDuration() { return timeoutDuration; }
        public void setTimeoutDuration(long timeoutDuration) { this.timeoutDuration = timeoutDuration; }
        
        @Override
        public String toString() {
            return String.format("RateLimiterConfig{limitForPeriod=%d, refreshPeriod=%dms, timeout=%dms}",
                    limitForPeriod, limitRefreshPeriod, timeoutDuration);
        }
    }
}