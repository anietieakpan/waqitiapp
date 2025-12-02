package com.waqiti.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Map;

/**
 * Configuration properties for rate limiting
 */
@Data
@Validated
@ConfigurationProperties(prefix = "waqiti.security.ratelimit")
public class RateLimitProperties {
    
    /**
     * Enable rate limiting
     */
    private boolean enabled = true;
    
    /**
     * Global rate limit settings
     */
    private Global global = new Global();
    
    /**
     * Per-endpoint rate limit settings
     */
    private Map<String, EndpointLimit> endpoints = Map.of(
        "/api/v*/auth/login", new EndpointLimit(5, 300, 100),
        "/api/v*/auth/register", new EndpointLimit(3, 900, 10),
        "/api/v*/payments/**", new EndpointLimit(100, 60, 1000),
        "/api/v*/transfers/**", new EndpointLimit(50, 60, 500)
    );
    
    /**
     * Per-user rate limit settings
     */
    private PerUser perUser = new PerUser();
    
    /**
     * Distributed rate limiting with Redis
     */
    private Distributed distributed = new Distributed();
    
    @Data
    public static class Global {
        @Min(1)
        @Max(10000)
        private int requestsPerMinute = 1000;
        
        @Min(1)
        @Max(100000)
        private int burstCapacity = 2000;
        
        @Min(1)
        @Max(3600)
        private int windowSizeSeconds = 60;
    }
    
    @Data
    public static class EndpointLimit {
        @Min(1)
        private int requests;
        
        @Min(1)
        @Max(3600)
        private int windowSizeSeconds;
        
        @Min(1)
        private int burstCapacity;
        
        public EndpointLimit() {}
        
        public EndpointLimit(int requests, int windowSizeSeconds, int burstCapacity) {
            this.requests = requests;
            this.windowSizeSeconds = windowSizeSeconds;
            this.burstCapacity = burstCapacity;
        }
    }
    
    @Data
    public static class PerUser {
        @Min(1)
        @Max(1000)
        private int requestsPerMinute = 100;
        
        @Min(1)
        @Max(10000)
        private int burstCapacity = 200;
        
        @Min(1)
        @Max(3600)
        private int windowSizeSeconds = 60;
        
        /**
         * Different limits for different user roles
         */
        private Map<String, UserRoleLimit> roleLimits = Map.of(
            "ROLE_ADMIN", new UserRoleLimit(1000, 60, 2000),
            "ROLE_MERCHANT", new UserRoleLimit(500, 60, 1000),
            "ROLE_USER", new UserRoleLimit(100, 60, 200)
        );
    }
    
    @Data
    public static class UserRoleLimit {
        private int requestsPerMinute;
        private int windowSizeSeconds;
        private int burstCapacity;
        
        public UserRoleLimit() {}
        
        public UserRoleLimit(int requestsPerMinute, int windowSizeSeconds, int burstCapacity) {
            this.requestsPerMinute = requestsPerMinute;
            this.windowSizeSeconds = windowSizeSeconds;
            this.burstCapacity = burstCapacity;
        }
    }
    
    @Data
    public static class Distributed {
        private boolean enabled = true;
        private String keyPrefix = "rate_limit:";
        
        @Min(1)
        @Max(3600)
        private int syncIntervalSeconds = 10;
        
        private boolean fallbackToLocal = true;
    }
}