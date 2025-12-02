package com.waqiti.billingorchestrator.client;

import com.waqiti.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Feign Client for Notification Service
 *
 * Integrates with existing notification-service microservice
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@FeignClient(
    name = "notification-service",
    path = "/api/v1/notifications",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    /**
     * Send email notification
     *
     * Calls: POST /api/v1/notifications/email
     */
    @PostMapping("/email")
    ApiResponse<Void> sendEmail(
            @RequestBody EmailNotificationRequest request,
            @RequestHeader("X-Request-ID") String requestId);

    /**
     * Send SMS notification
     *
     * Calls: POST /api/v1/notifications/sms
     */
    @PostMapping("/sms")
    ApiResponse<Void> sendSms(
            @RequestBody SmsNotificationRequest request,
            @RequestHeader("X-Request-ID") String requestId);

    /**
     * Send push notification
     *
     * Calls: POST /api/v1/notifications/push
     */
    @PostMapping("/push")
    ApiResponse<Void> sendPush(
            @RequestBody PushNotificationRequest request,
            @RequestHeader("X-Request-ID") String requestId);

    /**
     * Email notification request DTO
     */
    record EmailNotificationRequest(
        UUID userId,
        String email,
        String subject,
        String body,
        String templateId,
        Map<String, Object> templateData
    ) {}

    /**
     * SMS notification request DTO
     */
    record SmsNotificationRequest(
        UUID userId,
        String phoneNumber,
        String message,
        String templateId,
        Map<String, Object> templateData
    ) {}

    /**
     * Push notification request DTO
     */
    record PushNotificationRequest(
        UUID userId,
        String title,
        String body,
        Map<String, Object> data
    ) {}
}
