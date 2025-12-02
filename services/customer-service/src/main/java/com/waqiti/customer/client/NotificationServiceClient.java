package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.EmailNotificationRequest;
import com.waqiti.customer.client.dto.NotificationRequest;
import com.waqiti.customer.client.dto.PushNotificationRequest;
import com.waqiti.customer.client.dto.SmsNotificationRequest;
import com.waqiti.customer.client.fallback.NotificationServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * Feign client for inter-service communication with notification-service.
 * Provides methods to send various types of notifications.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "notification-service",
    configuration = FeignClientConfig.class,
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    /**
     * Sends a general notification.
     *
     * @param request Notification request details
     */
    @PostMapping("/api/v1/notifications/send")
    void sendNotification(@Valid @RequestBody NotificationRequest request);

    /**
     * Sends an email notification.
     *
     * @param request Email notification request details
     */
    @PostMapping("/api/v1/notifications/email")
    void sendEmail(@Valid @RequestBody EmailNotificationRequest request);

    /**
     * Sends an SMS notification.
     *
     * @param request SMS notification request details
     */
    @PostMapping("/api/v1/notifications/sms")
    void sendSms(@Valid @RequestBody SmsNotificationRequest request);

    /**
     * Sends a push notification.
     *
     * @param request Push notification request details
     */
    @PostMapping("/api/v1/notifications/push")
    void sendPushNotification(@Valid @RequestBody PushNotificationRequest request);
}
