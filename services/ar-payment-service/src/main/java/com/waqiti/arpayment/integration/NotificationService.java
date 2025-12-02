package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.integration.client.NotificationServiceClient;
import com.waqiti.arpayment.integration.dto.NotificationRequest;
import com.waqiti.arpayment.integration.dto.NotificationResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Notification service wrapper with async support and error handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationServiceClient notificationServiceClient;
    private final MeterRegistry meterRegistry;

    /**
     * Send AR payment notification asynchronously
     */
    @Async
    public CompletableFuture<NotificationResult> sendARPaymentNotificationAsync(
            UUID senderId, UUID recipientId, BigDecimal amount,
            String currency, String experienceType, String screenshotUrl) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Sending AR payment notification to user: {} from: {} amount: {} {}",
                        recipientId, senderId, amount, currency);

                NotificationResult result = notificationServiceClient.sendARPaymentNotification(
                        senderId, recipientId, amount, currency, experienceType, screenshotUrl);

                meterRegistry.counter("notification.ar_payment",
                        "sent", String.valueOf(result.isSent())).increment();

                return result;

            } catch (Exception e) {
                log.error("Failed to send AR payment notification to user: {}", recipientId, e);
                meterRegistry.counter("notification.error").increment();

                return NotificationResult.builder()
                        .sent(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Send generic notification
     */
    public NotificationResult sendNotification(NotificationRequest request) {
        try {
            log.debug("Sending {} notification to {} recipients",
                    request.getNotificationType(), request.getRecipientIds().size());

            NotificationResult result = notificationServiceClient.sendNotification(request);

            meterRegistry.counter("notification.sent",
                    "type", request.getNotificationType(),
                    "success", String.valueOf(result.isSent())).increment();

            return result;

        } catch (Exception e) {
            log.error("Notification send failed", e);
            meterRegistry.counter("notification.error").increment();

            return NotificationResult.builder()
                    .sent(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Send push notification asynchronously
     */
    @Async
    public CompletableFuture<NotificationResult> sendPushNotificationAsync(
            UUID userId, String title, String message, String data) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendPushNotification(userId, title, message, data);
            } catch (Exception e) {
                log.error("Push notification failed for user: {}", userId, e);
                return NotificationResult.builder().sent(false).errorMessage(e.getMessage()).build();
            }
        });
    }
}
