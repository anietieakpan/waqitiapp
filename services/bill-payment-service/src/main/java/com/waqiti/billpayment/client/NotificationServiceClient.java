package com.waqiti.billpayment.client;

import com.waqiti.billpayment.client.dto.NotificationRequest;
import com.waqiti.billpayment.client.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Notification Service
 * Handles email, SMS, and push notifications
 */
@FeignClient(
    name = "notification-service",
    url = "${notification.service.url:http://localhost:8092}",
    configuration = NotificationServiceClientConfig.class
)
public interface NotificationServiceClient {

    /**
     * Send notification (email, SMS, or push)
     */
    @PostMapping("/api/v1/notifications/send")
    NotificationResponse send(@RequestBody NotificationRequest request);
}
