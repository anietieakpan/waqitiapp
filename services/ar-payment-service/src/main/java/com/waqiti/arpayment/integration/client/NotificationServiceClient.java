package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.integration.dto.NotificationRequest;
import com.waqiti.arpayment.integration.dto.NotificationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign client for Notification Service integration
 * Provides multi-channel notification capabilities
 */
@FeignClient(
    name = "notification-service",
    url = "${feign.client.config.notification-service.url:http://notification-service/api/v1}",
    fallback = NotificationServiceFallback.class
)
public interface NotificationServiceClient {

    /**
     * Send AR payment notification
     * @param senderId Sender user ID
     * @param recipientId Recipient user ID
     * @param amount Payment amount
     * @param currency Currency code
     * @param experienceType AR experience type
     * @param screenshotUrl AR screenshot URL
     * @return Notification result
     */
    @PostMapping("/notifications/ar-payment")
    NotificationResult sendARPaymentNotification(
        @RequestParam("senderId") UUID senderId,
        @RequestParam("recipientId") UUID recipientId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("currency") String currency,
        @RequestParam("experienceType") String experienceType,
        @RequestParam(value = "screenshotUrl", required = false) String screenshotUrl
    );

    /**
     * Send generic notification
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/notifications/send")
    NotificationResult sendNotification(@RequestBody NotificationRequest request);

    /**
     * Send push notification
     * @param userId User ID
     * @param title Notification title
     * @param message Notification message
     * @param data Additional data
     * @return Notification result
     */
    @PostMapping("/notifications/push")
    NotificationResult sendPushNotification(
        @RequestParam("userId") UUID userId,
        @RequestParam("title") String title,
        @RequestParam("message") String message,
        @RequestParam(value = "data", required = false) String data
    );

    /**
     * Send email notification
     * @param userId User ID
     * @param subject Email subject
     * @param body Email body
     * @param isHtml Whether body is HTML
     * @return Notification result
     */
    @PostMapping("/notifications/email")
    NotificationResult sendEmailNotification(
        @RequestParam("userId") UUID userId,
        @RequestParam("subject") String subject,
        @RequestParam("body") String body,
        @RequestParam(value = "isHtml", defaultValue = "false") boolean isHtml
    );
}
