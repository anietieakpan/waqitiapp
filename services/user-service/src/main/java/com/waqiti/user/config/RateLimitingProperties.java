package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "user.rate-limiting")
public class RateLimitingProperties {

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