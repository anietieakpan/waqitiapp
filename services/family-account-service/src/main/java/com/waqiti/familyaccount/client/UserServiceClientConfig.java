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
 * User Service Client Configuration
 *
 * Configures Feign client for user-service with:
 * - Request interceptors for JWT token propagation
 * - Custom error decoder for better error handling
 * - Logging configuration
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Configuration
@Slf4j
public class UserServiceClientConfig {

    @Value("${service.auth.enabled:true}")
    private boolean serviceAuthEnabled;

    @Value("${service.auth.client-id:family-account-service}")
    private String clientId;

    /**
     * Request interceptor to add JWT authentication header to all requests
     */
    @Bean
    public RequestInterceptor userServiceRequestInterceptor() {
        return requestTemplate -> {
            if (serviceAuthEnabled) {
                try {
                    // Get JWT token from Security Context
                    var authentication = SecurityContextHolder.getContext().getAuthentication();

                    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                        String token = jwt.getTokenValue();
                        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        log.debug("Added JWT token to user-service request");
                    } else {
                        log.warn("No JWT token found in security context for user-service request");
                    }
                } catch (Exception e) {
                    log.error("Error adding authentication to user-service request", e);
                }
            }

            // Add service-to-service authentication header
            requestTemplate.header("X-Client-Id", clientId);
            requestTemplate.header("X-Service-Name", "family-account-service");
        };
    }

    /**
     * Custom error decoder for user-service responses
     */
    @Bean
    public ErrorDecoder userServiceErrorDecoder() {
        return (methodKey, response) -> {
            log.error("User service error - Method: {}, Status: {}, Reason: {}",
                      methodKey, response.status(), response.reason());

            return switch (response.status()) {
                case 400 -> new IllegalArgumentException(
                    "Invalid request to user-service: " + response.reason());
                case 401 -> new SecurityException(
                    "Unauthorized access to user-service: " + response.reason());
                case 403 -> new SecurityException(
                    "Forbidden access to user-service: " + response.reason());
                case 404 -> new IllegalArgumentException(
                    "User not found in user-service: " + response.reason());
                case 503 -> new RuntimeException(
                    "User service unavailable: " + response.reason());
                default -> new RuntimeException(
                    "User service error: " + response.status() + " - " + response.reason());
            };
        };
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level userServiceFeignLoggerLevel() {
        return Logger.Level.BASIC; // NONE, BASIC, HEADERS, FULL
    }
}
