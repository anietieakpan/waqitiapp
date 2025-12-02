package com.waqiti.security.config;

import com.waqiti.security.interceptor.AuthenticationSecurityInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Security Interceptor Configuration
 * 
 * CRITICAL SECURITY: Configures automatic authentication and authorization
 * security checks for all HTTP requests across the application.
 * 
 * This configuration ensures that:
 * - All API endpoints are protected by default
 * - Security checks are applied consistently
 * - Public endpoints are properly excluded
 * - Security interceptors are properly ordered
 * - Error handling is configured correctly
 * 
 * CONFIGURATION FEATURES:
 * - Automatic registration of security interceptors
 * - Configurable endpoint patterns for inclusion/exclusion
 * - Proper ordering with other interceptors
 * - Environment-specific configuration support
 * - Performance optimization for high-traffic endpoints
 * 
 * SECURITY ENFORCEMENT:
 * - Prevents authentication bypass vulnerabilities
 * - Enforces consistent authorization checks
 * - Provides comprehensive audit trails
 * - Enables real-time threat detection
 * - Supports compliance requirements
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityInterceptorConfig implements WebMvcConfigurer {

    private final AuthenticationSecurityInterceptor authenticationSecurityInterceptor;

    @Value("${security.interceptor.enabled:true}")
    private boolean interceptorEnabled;

    @Value("${security.interceptor.order:100}")
    private int interceptorOrder;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!interceptorEnabled) {
            log.warn("Security interceptors are DISABLED - This should only occur in development/testing");
            return;
        }

        log.info("Registering authentication security interceptor with order: {}", interceptorOrder);

        registry.addInterceptor(authenticationSecurityInterceptor)
            .addPathPatterns(
                "/api/**",           // All API endpoints
                "/admin/**",         // Admin endpoints
                "/internal/**"       // Internal service endpoints
            )
            .excludePathPatterns(
                "/actuator/**",      // Spring Boot Actuator endpoints
                "/health/**",        // Health check endpoints
                "/metrics/**",       // Metrics endpoints
                "/swagger-ui/**",    // Swagger UI
                "/v3/api-docs/**",   // OpenAPI docs
                "/api/v1/auth/**",   // Authentication endpoints
                "/api/v1/public/**", // Public endpoints
                "/webhooks/**",      // Webhook endpoints (have their own security)
                "/error",            // Error page
                "/favicon.ico"       // Static resources
            )
            .order(interceptorOrder);

        log.info("Authentication security interceptor registered successfully");
    }
}