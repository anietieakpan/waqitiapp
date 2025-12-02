package com.waqiti.frauddetection.service;

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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with the existing notification-service for fraud alerts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification-service.url:http://notification-service}")
    private String notificationServiceUrl;

    @Value("${fraud.notifications.enabled:true}")
    private boolean notificationsEnabled;

    /**
     * Send fraud alert notification
     */
    @Async("notificationTaskExecutor")
    public CompletableFuture<Void> sendNotification(String type, Map<String, Object> data) {
        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications/send";
                
                Map<String, Object> notification = Map.of(
                    "type", type,
                    "data", data,
                    "timestamp", java.time.Instant.now()
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(notification, headers);
                
                restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
                
                log.debug("Notification sent to notification-service: {}", type);
                
            } catch (Exception e) {
                log.error("Failed to send notification to notification-service", e);
            }
        });
    }

    /**
     * Send critical alert
     */
    @Async("notificationTaskExecutor")
    public CompletableFuture<Void> sendCriticalAlert(String title, String message) {
        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications/critical-alert";
                
                Map<String, Object> alert = Map.of(
                    "title", title,
                    "message", message,
                    "severity", "CRITICAL",
                    "source", "fraud-detection-service",
                    "timestamp", java.time.Instant.now()
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(alert, headers);
                
                restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
                
                log.info("Critical alert sent to notification-service: {}", title);
                
            } catch (Exception e) {
                log.error("Failed to send critical alert to notification-service", e);
            }
        });
    }

    /**
     * Send escalation alert
     */
    @Async("notificationTaskExecutor")
    public CompletableFuture<Void> sendEscalationAlert(String title, String message, String severity, Map<String, Object> context) {
        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications/escalation";
                
                Map<String, Object> alert = Map.of(
                    "title", title,
                    "message", message,
                    "severity", severity,
                    "context", context,
                    "source", "fraud-detection-service",
                    "timestamp", java.time.Instant.now()
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(alert, headers);
                
                restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
                
                log.warn("Escalation alert sent to notification-service: {}", title);
                
            } catch (Exception e) {
                log.error("Failed to send escalation alert to notification-service", e);
            }
        });
    }

    /**
     * Health check for notification service
     */
    public boolean isNotificationServiceHealthy() {
        try {
            String url = notificationServiceUrl + "/actuator/health";
            restTemplate.getForEntity(url, String.class);
            return true;
        } catch (Exception e) {
            log.debug("Notification service health check failed", e);
            return false;
        }
    }
}