package com.waqiti.familyaccount.client;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Security Service Client Configuration
 *
 * Configures Feign client for security-service with enhanced security headers.
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Configuration
@Slf4j
public class SecurityServiceClientConfig {

    @Value("${service.auth.enabled:true}")
    private boolean serviceAuthEnabled;

    @Value("${service.auth.client-id:family-account-service}")
    private String clientId;

    /**
     * Request interceptor to add JWT authentication header
     */
    @Bean
    public RequestInterceptor securityServiceRequestInterceptor() {
        return requestTemplate -> {
            if (serviceAuthEnabled) {
                try {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
                    }
                } catch (Exception e) {
                    log.error("Error adding authentication to security-service request", e);
                }
            }

            requestTemplate.header("X-Client-Id", clientId);
            requestTemplate.header("X-Service-Name", "family-account-service");
        };
    }

    /**
     * Custom error decoder for security-service responses
     */
    @Bean
    public ErrorDecoder securityServiceErrorDecoder() {
        return (methodKey, response) -> {
            log.error("Security service error - Method: {}, Status: {}, Reason: {}",
                      methodKey, response.status(), response.reason());

            return switch (response.status()) {
                case 400 -> new IllegalArgumentException(
                    "Invalid security request: " + response.reason());
                case 401 -> new SecurityException(
                    "Unauthorized security service access: " + response.reason());
                case 403 -> new SecurityException(
                    "Forbidden security operation: " + response.reason());
                case 429 -> new RuntimeException(
                    "Security service rate limit exceeded: " + response.reason());
                case 503 -> new RuntimeException(
                    "Security service unavailable: " + response.reason());
                default -> new RuntimeException(
                    "Security service error: " + response.status() + " - " + response.reason());
            };
        };
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level securityServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
