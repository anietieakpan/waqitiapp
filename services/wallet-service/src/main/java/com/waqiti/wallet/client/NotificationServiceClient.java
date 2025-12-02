package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.WalletClosureNotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Notification Service
 *
 * Handles communication with the notification-service for sending
 * wallet-related notifications to users.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@FeignClient(
    name = "notification-service",
    url = "${services.notification-service.url:http://notification-service:8084}",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    /**
     * Send wallet closure notification to user
     *
     * @param request wallet closure notification request
     */
    @PostMapping("/api/v1/notifications/wallet-closure")
    @CircuitBreaker(name = "notification-service")
    void sendWalletClosureNotification(@RequestBody WalletClosureNotificationRequest request);
}
