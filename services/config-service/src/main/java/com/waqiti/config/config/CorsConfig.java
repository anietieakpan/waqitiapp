package com.waqiti.config.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for Config Service
 *
 * Security: Whitelist only trusted origins to prevent unauthorized cross-origin requests
 * PCI-DSS Requirement: Proper CORS configuration prevents CSRF attacks
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:https://localhost:3000,https://localhost:3001,https://admin.waqiti.local}")
    private String[] allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${cors.allowed-headers:Authorization,Content-Type,X-Requested-With,X-CSRF-TOKEN}")
    private String[] allowedHeaders;

    @Value("${cors.max-age:3600}")
    private Long maxAge;

    @Value("${cors.allow-credentials:true}")
    private Boolean allowCredentials;

    /**
     * Configure CORS with whitelisted origins for security
     *
     * Production deployment MUST set CORS_ALLOWED_ORIGINS environment variable
     * to actual frontend URLs (e.g., https://app.example.com, https://admin.example.com)
     *
     * @return CorsConfigurationSource with security-hardened CORS settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Whitelist allowed origins - NEVER use "*" in production
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));

        // Allowed headers (including CSRF token)
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));

        // Expose headers that clients can access
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-CSRF-TOKEN",
                "X-Total-Count",
                "X-Page-Number",
                "X-Page-Size"
        ));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(allowCredentials);

        // Cache preflight responses for 1 hour
        configuration.setMaxAge(maxAge);

        // Apply CORS configuration to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
