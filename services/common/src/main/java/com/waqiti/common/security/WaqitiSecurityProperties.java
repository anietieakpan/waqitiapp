package com.waqiti.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Configuration properties for Waqiti Security features
 */
@Data
@ConfigurationProperties(prefix = "waqiti.security.common")
public class WaqitiSecurityProperties {

    /**
     * Enable/disable security auto-configuration
     */
    private boolean enabled = true;

    /**
     * Security headers configuration
     */
    @NestedConfigurationProperty
    private SecurityHeaders headers = new SecurityHeaders();

    /**
     * Content Security Policy configuration
     */
    @NestedConfigurationProperty
    private ContentSecurityPolicy csp = new ContentSecurityPolicy();

    /**
     * CORS configuration
     */
    @NestedConfigurationProperty
    private Cors cors = new Cors();

    /**
     * Security monitoring configuration
     */
    @NestedConfigurationProperty
    private Monitoring monitoring = new Monitoring();

    @Data
    public static class SecurityHeaders {
        /**
         * Enable/disable security headers
         */
        private boolean enabled = true;

        /**
         * HSTS configuration
         */
        @NestedConfigurationProperty
        private Hsts hsts = new Hsts();

        /**
         * Frame options configuration
         */
        private String frameOptions = "DENY";

        /**
         * Referrer policy
         */
        private String referrerPolicy = "strict-origin-when-cross-origin";

        /**
         * Custom server header
         */
        private String serverHeader = "";

        /**
         * Expect-CT configuration
         */
        @NestedConfigurationProperty
        private ExpectCt expectCt = new ExpectCt();

        @Data
        public static class Hsts {
            private long maxAge = 31536000; // 1 year
            private boolean includeSubdomains = true;
            private boolean preload = true;
        }

        @Data
        public static class ExpectCt {
            private long maxAge = 86400; // 24 hours
            private boolean enforce = true;
            private String reportUri = "";
        }
    }

    @Data
    public static class ContentSecurityPolicy {
        /**
         * Enable/disable CSP
         */
        private boolean enabled = true;

        /**
         * CSP report URI
         */
        private String reportUri = "";

        /**
         * CSP report-only mode
         */
        private boolean reportOnly = false;

        /**
         * Custom CSP directives
         */
        private String customDirectives = "";

        /**
         * Nonce generation for scripts and styles
         */
        private boolean useNonce = true;

        /**
         * Allowed script sources
         */
        private List<String> scriptSources = List.of(
            "'self'",
            "https://cdnjs.cloudflare.com",
            "https://cdn.jsdelivr.net"
        );

        /**
         * Allowed style sources
         */
        private List<String> styleSources = List.of(
            "'self'",
            "'unsafe-inline'",
            "https://fonts.googleapis.com",
            "https://cdnjs.cloudflare.com"
        );

        /**
         * Allowed connect sources (API endpoints)
         */
        private List<String> connectSources = List.of(
            "'self'",
            "https://api.example.com",
            "https://*.waqiti.com",
            "wss://websocket.waqiti.com"
        );
    }

    @Data
    public static class Cors {
        /**
         * Enable/disable CORS
         */
        private boolean enabled = true;

        /**
         * Allowed origins
         */
        private List<String> allowedOrigins = List.of(
            "https://*.waqiti.com",
            "https://api.example.com",
            "https://api.example.com"
        );

        /**
         * Allowed methods
         */
        private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        );

        /**
         * Allowed headers
         */
        private List<String> allowedHeaders = List.of(
            "Content-Type", "Authorization", "X-Requested-With",
            "X-CSRF-TOKEN", "X-API-Key", "Accept"
        );

        /**
         * Exposed headers
         */
        private List<String> exposedHeaders = List.of(
            "X-Total-Count", "X-Page-Count", "Link"
        );

        /**
         * Allow credentials
         */
        private boolean allowCredentials = true;

        /**
         * Max age for preflight requests
         */
        private long maxAge = 3600L;
    }

    @Data
    public static class Monitoring {
        /**
         * Enable security metrics collection
         */
        private boolean metricsEnabled = true;

        /**
         * Enable security event logging
         */
        private boolean eventLoggingEnabled = true;

        /**
         * Security alert thresholds
         */
        @NestedConfigurationProperty
        private AlertThresholds alertThresholds = new AlertThresholds();

        @Data
        public static class AlertThresholds {
            /**
             * Max failed authentication attempts per minute
             */
            private int maxFailedAuthPerMinute = 10;

            /**
             * Max suspicious requests per minute
             */
            private int maxSuspiciousRequestsPerMinute = 50;

            /**
             * Max CSP violations per minute
             */
            private int maxCspViolationsPerMinute = 20;
        }
    }
}