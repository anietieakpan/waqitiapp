package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.integration.dto.NotificationRequest;
import com.waqiti.arpayment.integration.dto.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fallback implementation for Notification Service client
 * Logs failed notifications and queues for retry
 */
@Slf4j
@Component
public class NotificationServiceFallback implements NotificationServiceClient {

    @Override
    public NotificationResult sendARPaymentNotification(UUID senderId, UUID recipientId,
                                                        BigDecimal amount, String currency,
                                                        String experienceType, String screenshotUrl) {
        log.error("Notification service unavailable - AR payment notification not sent to user: {}", recipientId);
        // TODO: Queue notification for retry when service is available
        return NotificationResult.builder()
                .sent(false)
                .errorMessage("Notification service unavailable")
                .queuedForRetry(true)
                .build();
    }

    @Override
    public NotificationResult sendNotification(NotificationRequest request) {
        log.error("Notification service unavailable - notification not sent");
        return NotificationResult.builder()
                .sent(false)
                .errorMessage("Notification service unavailable")
                .queuedForRetry(true)
                .build();
    }

    @Override
    public NotificationResult sendPushNotification(UUID userId, String title, String message, String data) {
        log.error("Notification service unavailable - push notification not sent to user: {}", userId);
        return NotificationResult.builder()
                .sent(false)
                .errorMessage("Notification service unavailable")
                .queuedForRetry(true)
                .build();
    }

    @Override
    public NotificationResult sendEmailNotification(UUID userId, String subject, String body, boolean isHtml) {
        log.error("Notification service unavailable - email notification not sent to user: {}", userId);
        return NotificationResult.builder()
                .sent(false)
                .errorMessage("Notification service unavailable")
                .queuedForRetry(true)
                .build();
    }
}
