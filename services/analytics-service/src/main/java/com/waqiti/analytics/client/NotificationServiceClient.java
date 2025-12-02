package com.waqiti.analytics.client;

import com.waqiti.analytics.dto.notification.NotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for Notification Service
 *
 * Sends notifications through the centralized notification-service microservice.
 * Supports multiple channels: Email, SMS, Push, In-App notifications.
 *
 * Circuit Breaker: Protects against notification-service failures
 * Retry: Attempts delivery 3 times with exponential backoff
 * Fallback: Publishes to Kafka topic if service unavailable
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@FeignClient(
    name = "notification-service",
    path = "/api/v1/notifications",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    /**
     * Send notification through notification-service
     *
     * @param request Notification request with recipients, channels, and content
     */
    @PostMapping("/send")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notificationService")
    void sendNotification(@RequestBody NotificationRequest request);
}
