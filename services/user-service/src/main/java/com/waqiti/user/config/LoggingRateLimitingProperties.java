package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Logging rate limiting configuration properties to resolve Qodana configuration issues
 */
@Data
@Component
@ConfigurationProperties(prefix = "logging.rate-limiting")
public class LoggingRateLimitingProperties {

    private boolean enabled = true;
    private String keyPrefix = "waqiti:user:rate-limit:";
    private int bucketExpirationMinutes = 60;
    private DefaultConfig defaultConfig = new DefaultConfig();
    private Map<String, ServiceConfig> services = new HashMap<>();

    @Data
    public static class DefaultConfig {
        private int capacity = 200;
        private int refillTokens = 200;
        private int refillPeriodMinutes = 1;
    }

    @Data
    public static class ServiceConfig {
        private int capacity = 200;
        private int refillTokens = 200;
        private int refillPeriodMinutes = 1;
        private Map<String, EndpointConfig> endpoints = new HashMap<>();
    }

    @Data
    public static class EndpointConfig {
        private int capacity;
        private int refillTokens;
        private int refillPeriodMinutes;
        private int tokens = 1;
    }
}