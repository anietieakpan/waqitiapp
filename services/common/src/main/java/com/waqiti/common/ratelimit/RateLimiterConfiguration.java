package com.waqiti.common.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise-grade rate limiter configuration with Redis support
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "rate-limiter")
@Data
public class RateLimiterConfiguration {

    private Map<String, EndpointConfig> endpoints = new HashMap<>();
    private GlobalConfig global = new GlobalConfig();
    private boolean enabled = true;
    private boolean useRedis = true;
    private String redisKeyPrefix = "rate-limiter:";
    
    // In-memory counters for fallback
    private final Map<String, AtomicLong> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> windowStartTimes = new ConcurrentHashMap<>();

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.of(createDefaultConfig());
    }

    @Bean
    public RateLimiter defaultRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("default", createDefaultConfig());
    }

    /**
     * Create default rate limiter configuration
     */
    private RateLimiterConfig createDefaultConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(global.getRefreshPeriodSeconds()))
                .limitForPeriod(global.getDefaultLimit())
                .timeoutDuration(Duration.ofMillis(global.getTimeoutMillis()))
                .build();
    }

    /**
     * Get or create rate limiter for endpoint
     */
    public RateLimiter getRateLimiterForEndpoint(String endpoint, RateLimiterRegistry registry) {
        EndpointConfig config = endpoints.getOrDefault(endpoint, 
                EndpointConfig.defaultConfig(global.getDefaultLimit()));
        
        String limiterName = "endpoint-" + endpoint.replace("/", "-");
        
        return registry.rateLimiter(limiterName, RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(config.getRefreshPeriodSeconds()))
                .limitForPeriod(config.getLimit())
                .timeoutDuration(Duration.ofMillis(config.getTimeoutMillis()))
                .build());
    }

    /**
     * Check if request is allowed using token bucket algorithm
     */
    public boolean isRequestAllowed(String key, int limit, long windowSizeMillis) {
        if (!enabled) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        
        AtomicLong counter = requestCounters.computeIfAbsent(key, k -> new AtomicLong(0));
        AtomicLong windowStart = windowStartTimes.computeIfAbsent(key, k -> new AtomicLong(currentTime));
        
        // Check if we need to reset the window
        if (currentTime - windowStart.get() > windowSizeMillis) {
            windowStart.set(currentTime);
            counter.set(0);
        }
        
        // Check if under limit
        if (counter.incrementAndGet() <= limit) {
            return true;
        }
        
        counter.decrementAndGet();
        return false;
    }

    /**
     * Get remaining requests for a key
     */
    public long getRemainingRequests(String key, int limit) {
        AtomicLong counter = requestCounters.get(key);
        if (counter == null) {
            return limit;
        }
        return Math.max(0, limit - counter.get());
    }

    /**
     * Reset rate limiter for a key
     */
    public void resetRateLimiter(String key) {
        requestCounters.remove(key);
        windowStartTimes.remove(key);
    }

    /**
     * Get window reset time
     */
    public long getWindowResetTime(String key, long windowSizeMillis) {
        AtomicLong windowStart = windowStartTimes.get(key);
        if (windowStart == null) {
            return System.currentTimeMillis() + windowSizeMillis;
        }
        return windowStart.get() + windowSizeMillis;
    }

    /**
     * Global configuration
     */
    @Data
    public static class GlobalConfig {
        private int defaultLimit = 100;
        private long refreshPeriodSeconds = 1;
        private long timeoutMillis = 100;
        private boolean enablePerUser = true;
        private boolean enablePerIp = true;
        private boolean enablePerEndpoint = true;
    }

    /**
     * Endpoint-specific configuration
     */
    @Data
    public static class EndpointConfig {
        private int limit;
        private long refreshPeriodSeconds = 1;
        private long timeoutMillis = 100;
        private boolean enabled = true;
        private String method = "ALL";
        
        public static EndpointConfig defaultConfig(int limit) {
            EndpointConfig config = new EndpointConfig();
            config.setLimit(limit);
            return config;
        }
    }

    /**
     * Create rate limiter with custom configuration
     */
    public RateLimiter createCustomRateLimiter(String name, int limit, Duration refreshPeriod) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(refreshPeriod)
                .limitForPeriod(limit)
                .timeoutDuration(Duration.ofMillis(global.getTimeoutMillis()))
                .build();
        
        return RateLimiter.of(name, config);
    }

    /**
     * Get rate limit key for user
     */
    public String getUserRateLimitKey(String userId, String endpoint) {
        return String.format("%suser:%s:%s", redisKeyPrefix, userId, endpoint);
    }

    /**
     * Get rate limit key for IP
     */
    public String getIpRateLimitKey(String ipAddress, String endpoint) {
        return String.format("%sip:%s:%s", redisKeyPrefix, ipAddress, endpoint);
    }

    /**
     * Check if endpoint is rate limited
     */
    public boolean isEndpointRateLimited(String endpoint) {
        if (!enabled) {
            return false;
        }
        
        EndpointConfig config = endpoints.get(endpoint);
        return config == null || config.isEnabled();
    }

    /**
     * Get limit for endpoint
     */
    public int getLimitForEndpoint(String endpoint) {
        EndpointConfig config = endpoints.get(endpoint);
        return config != null ? config.getLimit() : global.getDefaultLimit();
    }

    /**
     * Update endpoint configuration
     */
    public void updateEndpointConfig(String endpoint, EndpointConfig config) {
        endpoints.put(endpoint, config);
        log.info("Updated rate limiter config for endpoint: {} with limit: {}", 
                endpoint, config.getLimit());
    }

    /**
     * Remove endpoint configuration
     */
    public void removeEndpointConfig(String endpoint) {
        endpoints.remove(endpoint);
        log.info("Removed rate limiter config for endpoint: {}", endpoint);
    }

    /**
     * Get all endpoint configurations
     */
    public Map<String, EndpointConfig> getAllEndpointConfigs() {
        return new HashMap<>(endpoints);
    }

    /**
     * Clear all in-memory counters
     */
    public void clearAllCounters() {
        requestCounters.clear();
        windowStartTimes.clear();
        log.info("Cleared all rate limiter counters");
    }
}