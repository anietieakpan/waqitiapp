package com.waqiti.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

/**
 * CORS Configuration Properties
 *
 * HIGH-01 FIX (2025-11-22): Centralized CORS configuration
 *
 * PROBLEM SOLVED:
 * - Hardcoded CORS origins in @CrossOrigin annotations
 * - Development origins (localhost:3000) in production code
 * - No environment-specific CORS configuration
 *
 * SOLUTION:
 * - Externalize CORS configuration to application.yml
 * - Environment-specific allowed origins
 * - Validation of CORS settings at startup
 *
 * USAGE:
 * Instead of:
 *   @CrossOrigin(origins = {"http://localhost:3000", "https://api.example.com"})
 *
 * Use:
 *   @CrossOrigin(origins = "${cors.allowed-origins}")
 *
 * Or configure globally in WebMvcConfigurer
 *
 * CONFIGURATION:
 * # application-dev.yml
 * cors:
 *   enabled: true
 *   allowed-origins:
 *     - http://localhost:3000
 *     - http://localhost:3001
 *   allowed-methods:
 *     - GET
 *     - POST
 *     - PUT
 *     - DELETE
 *   allowed-headers:
 *     - "*"
 *   exposed-headers:
 *     - X-Total-Count
 *     - X-Correlation-Id
 *   allow-credentials: true
 *   max-age: 3600
 *
 * # application-prod.yml
 * cors:
 *   enabled: true
 *   allowed-origins:
 *     - https://api.example.com
 *     - https://api.example.com
 *   allowed-methods:
 *     - GET
 *     - POST
 *     - PUT
 *     - DELETE
 *   allowed-headers:
 *     - Authorization
 *     - Content-Type
 *     - X-Requested-With
 *   exposed-headers:
 *     - X-Total-Count
 *     - X-Correlation-Id
 *   allow-credentials: true
 *   max-age: 3600
 *
 * @author Waqiti Platform Team
 * @since 2025-11-22
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
@Validated
public class CorsConfigurationProperties {

    /**
     * Enable or disable CORS globally
     */
    private boolean enabled = true;

    /**
     * List of allowed origins
     * Examples:
     * - http://localhost:3000 (development)
     * - https://api.example.com (production)
     * - https://*.waqiti.com (wildcard subdomain)
     */
    @NotEmpty(message = "At least one allowed origin must be configured")
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * List of allowed HTTP methods
     * Default: GET, POST, PUT, DELETE, PATCH, OPTIONS
     */
    private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    );

    /**
     * List of allowed headers
     * Use "*" to allow all headers (not recommended for production)
     */
    private List<String> allowedHeaders = List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-Correlation-Id",
            "X-Device-Id",
            "X-Session-Id",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
    );

    /**
     * List of headers to expose to the client
     * These headers will be accessible via JavaScript
     */
    private List<String> exposedHeaders = List.of(
            "X-Total-Count",
            "X-Total-Pages",
            "X-Current-Page",
            "X-Correlation-Id",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset"
    );

    /**
     * Whether to allow credentials (cookies, authorization headers)
     * SECURITY: Set to true only if you need to send credentials cross-origin
     */
    private boolean allowCredentials = true;

    /**
     * How long (in seconds) the browser should cache preflight responses
     * Default: 3600 (1 hour)
     */
    private Long maxAge = 3600L;

    /**
     * Validate configuration on startup
     */
    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (enabled && allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "CORS is enabled but no allowed origins are configured. " +
                    "Set cors.allowed-origins in application.yml or disable CORS with cors.enabled=false"
            );
        }

        // Warn if wildcard origin is used with credentials
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: Cannot use wildcard origin (*) with allowCredentials=true. " +
                    "Specify explicit origins or set allowCredentials=false"
            );
        }

        // Warn if localhost origins are present (likely development configuration)
        long localhostOrigins = allowedOrigins.stream()
                .filter(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"))
                .count();

        if (localhostOrigins > 0) {
            String profile = System.getProperty("spring.profiles.active", "unknown");
            if (profile.contains("prod")) {
                throw new IllegalStateException(
                        "SECURITY WARNING: Localhost origins detected in production configuration: " +
                        allowedOrigins + ". Remove localhost origins for production deployment."
                );
            }
        }

        // Log configuration for verification
        org.slf4j.LoggerFactory.getLogger(CorsConfigurationProperties.class)
                .info("CORS Configuration: enabled={}, origins={}, credentials={}",
                        enabled, allowedOrigins, allowCredentials);
    }

    /**
     * Check if an origin is allowed
     *
     * @param origin The origin to check
     * @return true if the origin is allowed
     */
    public boolean isOriginAllowed(String origin) {
        if (!enabled) {
            return false;
        }

        return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
    }

    /**
     * Get origins as array (for Spring configuration)
     */
    public String[] getAllowedOriginsArray() {
        return allowedOrigins.toArray(new String[0]);
    }

    /**
     * Get methods as array (for Spring configuration)
     */
    public String[] getAllowedMethodsArray() {
        return allowedMethods.toArray(new String[0]);
    }

    /**
     * Get headers as array (for Spring configuration)
     */
    public String[] getAllowedHeadersArray() {
        return allowedHeaders.toArray(new String[0]);
    }

    /**
     * Get exposed headers as array (for Spring configuration)
     */
    public String[] getExposedHeadersArray() {
        return exposedHeaders.toArray(new String[0]);
    }
}
