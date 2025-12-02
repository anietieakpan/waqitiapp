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
 * Wallet Service Client Configuration
 *
 * Configures Feign client for wallet-service with enhanced error handling
 * for financial operations.
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Configuration
@Slf4j
public class WalletServiceClientConfig {

    @Value("${service.auth.enabled:true}")
    private boolean serviceAuthEnabled;

    @Value("${service.auth.client-id:family-account-service}")
    private String clientId;

    /**
     * Request interceptor to add JWT authentication header
     */
    @Bean
    public RequestInterceptor walletServiceRequestInterceptor() {
        return requestTemplate -> {
            if (serviceAuthEnabled) {
                try {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
                        log.debug("Added JWT token to wallet-service request");
                    }
                } catch (Exception e) {
                    log.error("Error adding authentication to wallet-service request", e);
                }
            }

            requestTemplate.header("X-Client-Id", clientId);
            requestTemplate.header("X-Service-Name", "family-account-service");
            requestTemplate.header("X-Idempotency-Key", java.util.UUID.randomUUID().toString());
        };
    }

    /**
     * Custom error decoder for wallet-service responses with financial context
     */
    @Bean
    public ErrorDecoder walletServiceErrorDecoder() {
        return (methodKey, response) -> {
            log.error("Wallet service error - Method: {}, Status: {}, Reason: {}",
                      methodKey, response.status(), response.reason());

            return switch (response.status()) {
                case 400 -> new IllegalArgumentException(
                    "Invalid wallet operation: " + response.reason());
                case 401 -> new SecurityException(
                    "Unauthorized wallet access: " + response.reason());
                case 403 -> new SecurityException(
                    "Forbidden wallet operation: " + response.reason());
                case 404 -> new IllegalArgumentException(
                    "Wallet not found: " + response.reason());
                case 409 -> new IllegalStateException(
                    "Wallet conflict (possibly frozen or insufficient funds): " + response.reason());
                case 422 -> new IllegalArgumentException(
                    "Invalid transaction parameters: " + response.reason());
                case 503 -> new RuntimeException(
                    "Wallet service unavailable: " + response.reason());
                default -> new RuntimeException(
                    "Wallet service error: " + response.status() + " - " + response.reason());
            };
        };
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level walletServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
