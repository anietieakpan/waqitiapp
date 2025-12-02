package com.waqiti.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for security settings
 */
@Data
@Validated
@ConfigurationProperties(prefix = "waqiti.security")
public class SecurityProperties {
    
    /**
     * Enable security features
     */
    private boolean enabled = true;
    
    /**
     * Password policy settings
     */
    private PasswordPolicy passwordPolicy = new PasswordPolicy();
    
    /**
     * Session management settings
     */
    private SessionManagement sessionManagement = new SessionManagement();
    
    /**
     * CORS configuration
     */
    private Cors cors = new Cors();
    
    /**
     * Security headers configuration
     */
    private Headers headers = new Headers();
    
    /**
     * Monitoring and audit settings
     */
    private Monitoring monitoring = new Monitoring();
    
    @Data
    public static class PasswordPolicy {
        @Min(8)
        @Max(128)
        private int minLength = 12;
        
        @Min(1)
        @Max(10)
        private int maxRetries = 3;
        
        private boolean requireUppercase = true;
        private boolean requireLowercase = true;
        private boolean requireDigits = true;
        private boolean requireSpecialChars = true;
        
        @Min(1)
        @Max(90)
        private int expiryDays = 90;
        
        @Min(1)
        @Max(24)
        private int historyCount = 12;
    }
    
    @Data
    public static class SessionManagement {
        @Min(300)
        @Max(86400)
        private int timeoutSeconds = 3600;
        
        @Min(1)
        @Max(10)
        private int maxConcurrentSessions = 3;
        
        private boolean preventLoginAfterMaxSessions = true;
        private boolean invalidateOnAuth = true;
    }
    
    /**
     * CRITICAL P0 SECURITY FIX: CORS Configuration
     *
     * Fixed vulnerabilities:
     * 1. REMOVED wildcard origin "*" (was allowing ANY website to access API)
     * 2. DEFINED specific allowed origins (production, staging, development)
     * 3. REMOVED wildcard allowedHeaders "*" for better security
     * 4. Credentials only allowed with specific origins (not wildcard)
     *
     * Security Impact:
     * - Prevents CSRF attacks from malicious websites
     * - Prevents data theft via cross-origin requests
     * - Ensures only trusted domains can access authenticated endpoints
     *
     * Configuration:
     * - Set waqiti.security.cors.allowed-origins in environment variables
     * - For development: https://localhost:3000,http://localhost:3000
     * - For production: https://api.example.com,https://api.example.com
     *
     * @author Waqiti Engineering Team - Production Security Fix
     * @version 2.0.0
     */
    @Data
    public static class Cors {
        private boolean enabled = true;

        /**
         * SECURITY FIX: Specific allowed origins (NO WILDCARD!)
         * Must be configured via environment variables for each environment
         */
        private List<String> allowedOrigins = List.of(
            "https://api.example.com",
            "https://api.example.com",
            "https://api.example.com"
        );

        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");

        /**
         * SECURITY FIX: Specific allowed headers (NO WILDCARD!)
         * Only allow headers we actually use
         */
        private List<String> allowedHeaders = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Request-ID",
            "X-Correlation-ID",
            "X-CSRF-TOKEN",
            "X-Device-ID",
            "X-App-Version",
            "X-Platform"
        );

        private List<String> exposedHeaders = List.of(
            "Authorization",
            "X-Request-ID",
            "X-Correlation-ID",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset"
        );

        /**
         * Credentials allowed ONLY with specific origins (never with wildcard)
         */
        private boolean allowCredentials = true;

        @Min(0)
        @Max(86400)
        private long maxAge = 3600;
    }
    
    @Data
    public static class Headers {
        private boolean enableCsp = true;
        private boolean enableHsts = true;
        private boolean enableFrameOptions = true;
        private boolean enableContentTypeOptions = true;
        private boolean enableReferrerPolicy = true;
        private boolean enablePermissionsPolicy = true;
        
        private String contentSecurityPolicy = "default-src 'self'";
        private String referrerPolicy = "strict-origin-when-cross-origin";
        private long hstsMaxAge = 31536000;
    }
    
    @Data
    public static class Monitoring {
        private boolean enableAuditLog = true;
        private boolean enableSecurityMetrics = true;
        private boolean enableFailedLoginTracking = true;
        private boolean enableSuspiciousActivityDetection = true;
        
        @Min(1)
        @Max(100)
        private int maxFailedLoginAttempts = 5;
        
        @Min(60)
        @Max(3600)
        private int failedLoginLockoutSeconds = 300;
    }
}