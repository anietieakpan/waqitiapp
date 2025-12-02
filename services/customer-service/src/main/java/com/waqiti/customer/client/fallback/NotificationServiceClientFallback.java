package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.NotificationServiceClient;
import com.waqiti.customer.client.dto.EmailNotificationRequest;
import com.waqiti.customer.client.dto.NotificationRequest;
import com.waqiti.customer.client.dto.PushNotificationRequest;
import com.waqiti.customer.client.dto.SmsNotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for NotificationServiceClient.
 * Provides circuit breaker pattern implementation with safe default behavior
 * when notification-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void sendNotification(NotificationRequest request) {
        log.error("NotificationServiceClient.sendNotification fallback triggered for recipientId: {}. Notification not sent: {}",
                request != null ? request.getRecipientId() : "unknown",
                request != null ? request.getSubject() : "unknown");
    }

    @Override
    public void sendEmail(EmailNotificationRequest request) {
        log.error("NotificationServiceClient.sendEmail fallback triggered for recipient: {}. Email not sent: {}",
                request != null ? request.getTo() : "unknown",
                request != null ? request.getSubject() : "unknown");
    }

    @Override
    public void sendSms(SmsNotificationRequest request) {
        log.error("NotificationServiceClient.sendSms fallback triggered for phoneNumber: {}. SMS not sent.",
                request != null ? request.getPhoneNumber() : "unknown");
    }

    @Override
    public void sendPushNotification(PushNotificationRequest request) {
        log.error("NotificationServiceClient.sendPushNotification fallback triggered for userId: {}. Push notification not sent: {}",
                request != null ? request.getUserId() : "unknown",
                request != null ? request.getTitle() : "unknown");
    }
}
