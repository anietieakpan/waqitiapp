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
 * Notification Service Client Configuration
 *
 * Configures Feign client for notification-service.
 * Notifications are non-critical - failures are logged but don't break operations.
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Configuration
@Slf4j
public class NotificationServiceClientConfig {

    @Value("${service.auth.enabled:true}")
    private boolean serviceAuthEnabled;

    @Value("${service.auth.client-id:family-account-service}")
    private String clientId;

    /**
     * Request interceptor to add JWT authentication header
     */
    @Bean
    public RequestInterceptor notificationServiceRequestInterceptor() {
        return requestTemplate -> {
            if (serviceAuthEnabled) {
                try {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
                    }
                } catch (Exception e) {
                    log.error("Error adding authentication to notification-service request", e);
                }
            }

            requestTemplate.header("X-Client-Id", clientId);
            requestTemplate.header("X-Service-Name", "family-account-service");
        };
    }

    /**
     * Custom error decoder for notification-service responses
     * Logs errors but returns non-blocking exceptions since notifications are non-critical
     */
    @Bean
    public ErrorDecoder notificationServiceErrorDecoder() {
        return (methodKey, response) -> {
            // Log notification failures but don't throw blocking exceptions
            log.warn("Notification service error - Method: {}, Status: {}, Reason: {} - Operation continues",
                     methodKey, response.status(), response.reason());

            // Return a non-fatal exception that won't break the flow
            return new RuntimeException(
                "Notification delivery failed (non-fatal): " + response.status() + " - " + response.reason()
            );
        };
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level notificationServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
