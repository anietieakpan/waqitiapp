package com.waqiti.common.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for rate limiting
 */
@Data
@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitProperties {
    
    /**
     * Enable or disable rate limiting globally
     */
    private boolean enabled = true;
    
    /**
     * Redis key prefix for rate limit buckets
     */
    private String keyPrefix = "rate-limit:";
    
    /**
     * Bucket expiration time in minutes
     */
    private long bucketExpirationMinutes = 60;
    
    /**
     * Default rate limit configuration
     */
    private DefaultLimits defaultLimits = new DefaultLimits();
    
    /**
     * Service-specific rate limit configurations
     */
    private Map<String, ServiceLimits> services = new HashMap<>();
    
    /**
     * Endpoint-specific rate limit configurations
     */
    private Map<String, EndpointLimits> endpoints = new HashMap<>();
    
    @Data
    public static class DefaultLimits {
        private long capacity = 100;
        private long refillTokens = 100;
        private long refillPeriodMinutes = 1;
    }
    
    @Data
    public static class ServiceLimits {
        private long capacity;
        private long refillTokens;
        private long refillPeriodMinutes;
        private Map<String, EndpointLimits> endpoints = new HashMap<>();
    }
    
    @Data
    public static class EndpointLimits {
        private long capacity;
        private long refillTokens;
        private long refillPeriodMinutes;
        private int tokens = 1;
    }
}