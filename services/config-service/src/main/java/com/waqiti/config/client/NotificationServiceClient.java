package com.waqiti.config.client;

import com.waqiti.config.dto.ConfigurationChangeNotification;
import com.waqiti.config.dto.FeatureFlagChangeNotification;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with the existing notification-service
 * Avoids code duplication and leverages the robust notification infrastructure
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification-service.url:http://notification-service}")
    private String notificationServiceUrl;

    @Value("${config.notifications.enabled:true}")
    private boolean notificationsEnabled;

    /**
     * Send configuration change notification through the existing notification service
     */
    @Async("notificationTaskExecutor")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyConfigurationChangeFallback")
    @Retry(name = "notificationService")
    public CompletableFuture<Void> notifyConfigurationChange(ConfigurationChangeNotification notification) {
        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications/config-change";
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<ConfigurationChangeNotification> request = new HttpEntity<>(notification, headers);
                
                restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
                
                log.debug("Configuration change notification sent to notification-service for: {}", 
                    notification.getConfigKey());
                
            } catch (Exception e) {
                log.error("Failed to send configuration change notification to notification-service", e);
                // Don't throw exception to avoid breaking config operations
            }
        });
    }

    /**
     * Send feature flag change notification through the existing notification service
     */
    @Async("notificationTaskExecutor")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyFeatureFlagChangeFallback")
    @Retry(name = "notificationService")
    public CompletableFuture<Void> notifyFeatureFlagChange(FeatureFlagChangeNotification notification) {
        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications/feature-flag-change";
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<FeatureFlagChangeNotification> request = new HttpEntity<>(notification, headers);
                
                restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
                
                log.debug("Feature flag change notification sent to notification-service for: {}", 
                    notification.getFlagName());
                
            } catch (Exception e) {
                log.error("Failed to send feature flag change notification to notification-service", e);
            }
        });
    }

    /**
     * Send critical alert through the existing notification service
     */
    @Async("notificationTaskExecutor")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendCriticalAlertFallback")
    @Retry(name = "notificationService")
    public CompletableFuture<Void> sendCriticalAlert(String title, String message) {
        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications/critical-alert";
                
                CriticalAlertRequest alertRequest = CriticalAlertRequest.builder()
                    .title(title)
                    .message(message)
                    .severity("CRITICAL")
                    .source("config-service")
                    .timestamp(java.time.Instant.now())
                    .build();
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<CriticalAlertRequest> request = new HttpEntity<>(alertRequest, headers);
                
                restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
                
                log.info("Critical alert sent to notification-service: {}", title);
                
            } catch (Exception e) {
                log.error("Failed to send critical alert to notification-service", e);
            }
        });
    }

    /**
     * Health check for notification service connectivity
     */
    public boolean isNotificationServiceHealthy() {
        try {
            String url = notificationServiceUrl + "/actuator/health";
            restTemplate.getForEntity(url, String.class);
            return true;
        } catch (Exception e) {
            log.warn("Notification service health check failed", e);
            return false;
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class CriticalAlertRequest {
        private String title;
        private String message;
        private String severity;
        private String source;
        private java.time.Instant timestamp;
    }

    // Fallback methods for circuit breaker

    private CompletableFuture<Void> notifyConfigurationChangeFallback(ConfigurationChangeNotification notification, Exception e) {
        log.warn("Notification service circuit breaker activated for config change. Config: {}, Error: {}",
                notification.getConfigKey(), e.getMessage());
        // Configuration operation continues even if notification fails
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> notifyFeatureFlagChangeFallback(FeatureFlagChangeNotification notification, Exception e) {
        log.warn("Notification service circuit breaker activated for feature flag change. Flag: {}, Error: {}",
                notification.getFlagName(), e.getMessage());
        // Feature flag operation continues even if notification fails
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> sendCriticalAlertFallback(String title, String message, Exception e) {
        log.error("Notification service circuit breaker activated for critical alert. Title: {}, Error: {}",
                title, e.getMessage());
        // Log locally since notification service is down
        log.error("CRITICAL ALERT (notification service unavailable): {} - {}", title, message);
        return CompletableFuture.completedFuture(null);
    }
}