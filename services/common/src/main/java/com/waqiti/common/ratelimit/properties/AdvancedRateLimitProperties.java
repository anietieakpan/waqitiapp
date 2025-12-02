package com.waqiti.common.ratelimit.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for advanced rate limiting
 */
@Data
@ConfigurationProperties(prefix = "waqiti.rate-limit")
public class AdvancedRateLimitProperties {

    private boolean enabled = true;
    private Redis redis = new Redis();
    private Bucket bucket = new Bucket();
    private Rules rules = new Rules();
    private java.util.Map<String, EndpointConfig> endpoints = new java.util.HashMap<>();

    @Data
    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private int maxConnections = 100;
        private int maxIdle = 10;
        private int minIdle = 5;
        private int timeoutMs = 2000;
        
        // SSL support
        private boolean ssl = false;
        
        // Redis Sentinel support for HA
        private java.util.List<String> sentinels = new java.util.ArrayList<>();
        private String masterName = "mymaster";
        
        // Redis Cluster support
        private java.util.List<String> clusterNodes = new java.util.ArrayList<>();
        private int maxRedirections = 3;
        
        // Additional connection settings
        private long connectionTimeoutMs = 2000;
        private long socketTimeoutMs = 2000;
        private boolean testOnBorrow = true;
        private boolean testOnReturn = true;
        private boolean testWhileIdle = true;
        
        // Circuit breaker settings for Redis failures
        private boolean circuitBreakerEnabled = true;
        private int circuitBreakerFailureThreshold = 5;
        private long circuitBreakerRecoveryTimeMs = 30000;
    }

    @Data
    public static class Bucket {
        private long capacity = 100;
        private long refillTokens = 10;
        private long refillPeriodMs = 60000;
    }

    @Data
    public static class Rules {
        private int maxRequestsPerMinute = 60;
        private int maxRequestsPerHour = 1000;
        private int maxRequestsPerDay = 10000;
        private boolean enableBurstMode = true;
        private double burstMultiplier = 1.5;
    }

    @Data
    public static class EndpointConfig {
        private long capacity = 100;
        private long refillTokens = 10;
        private long refillPeriodSeconds = 60;
        private int maxRequestsPerMinute = 60;
        private int maxRequestsPerHour = 1000;
        private boolean enableBurstMode = true;
        private double burstMultiplier = 1.5;
        
        // Rate limiting strategies
        private String strategy = "TOKEN_BUCKET"; // TOKEN_BUCKET, SLIDING_WINDOW, FIXED_WINDOW
        private boolean enableUserRateLimit = true;
        private boolean enableIpRateLimit = true;
        private boolean enableGlobalRateLimit = false;
        
        // Priority and weight settings
        private int priority = 0;
        private double weight = 1.0;
        
        // Fallback behavior
        private boolean failOpen = true; // Allow requests when rate limiter fails
        private String fallbackAction = "ALLOW"; // ALLOW, DENY, THROTTLE
    }

    /**
     * Get default configuration for endpoints
     */
    public EndpointConfig getDefaultConfig() {
        EndpointConfig config = new EndpointConfig();
        config.setCapacity(bucket.getCapacity());
        config.setRefillTokens(bucket.getRefillTokens());
        config.setRefillPeriodSeconds(bucket.getRefillPeriodMs() / 1000);
        config.setMaxRequestsPerMinute(rules.getMaxRequestsPerMinute());
        config.setMaxRequestsPerHour(rules.getMaxRequestsPerHour());
        config.setEnableBurstMode(rules.isEnableBurstMode());
        config.setBurstMultiplier(rules.getBurstMultiplier());
        return config;
    }
}