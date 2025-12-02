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
 * KYC Service Client Configuration
 *
 * Configures Feign client for KYC service with extended timeouts
 * for compliance checks.
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Configuration
@Slf4j
public class KYCServiceClientConfig {

    @Value("${service.auth.enabled:true}")
    private boolean serviceAuthEnabled;

    @Value("${service.auth.client-id:family-account-service}")
    private String clientId;

    /**
     * Request interceptor to add JWT authentication header
     */
    @Bean
    public RequestInterceptor kycServiceRequestInterceptor() {
        return requestTemplate -> {
            if (serviceAuthEnabled) {
                try {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
                    }
                } catch (Exception e) {
                    log.error("Error adding authentication to KYC service request", e);
                }
            }

            requestTemplate.header("X-Client-Id", clientId);
            requestTemplate.header("X-Service-Name", "family-account-service");
        };
    }

    /**
     * Custom error decoder for KYC service responses
     */
    @Bean
    public ErrorDecoder kycServiceErrorDecoder() {
        return (methodKey, response) -> {
            log.error("KYC service error - Method: {}, Status: {}, Reason: {}",
                      methodKey, response.status(), response.reason());

            return switch (response.status()) {
                case 400 -> new IllegalArgumentException(
                    "Invalid KYC request: " + response.reason());
                case 401 -> new SecurityException(
                    "Unauthorized KYC service access: " + response.reason());
                case 403 -> new SecurityException(
                    "Forbidden KYC operation: " + response.reason());
                case 404 -> new IllegalArgumentException(
                    "KYC record not found: " + response.reason());
                case 422 -> new IllegalArgumentException(
                    "KYC verification incomplete or failed: " + response.reason());
                case 503 -> new RuntimeException(
                    "KYC service unavailable: " + response.reason());
                default -> new RuntimeException(
                    "KYC service error: " + response.status() + " - " + response.reason());
            };
        };
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level kycServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
