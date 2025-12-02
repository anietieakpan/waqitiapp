package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserCommunicationsDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for Notification Service integration
 * Used for GDPR data export to retrieve user communications history
 */
@FeignClient(
    name = "notification-service",
    url = "${services.notification-service.url:http://notification-service:8084}",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    /**
     * Get complete user communications history for GDPR export
     * Includes emails, SMS, push notifications sent to user
     *
     * @param userId User ID
     * @param correlationId Tracing correlation ID
     * @return Complete communications history
     */
    @GetMapping("/api/v1/notifications/users/{userId}/gdpr/communications")
    UserCommunicationsDataDTO getUserCommunications(
        @PathVariable("userId") String userId,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
