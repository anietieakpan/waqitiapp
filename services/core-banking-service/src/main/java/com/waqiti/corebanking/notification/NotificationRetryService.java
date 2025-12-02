package com.waqiti.corebanking.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.client.NotificationServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification Retry Service
 *
 * Manages failed notification retry queue with exponential backoff.
 * Scheduled worker processes pending notifications every minute.
 *
 * Features:
 * - Database-backed persistent queue (survives service restarts)
 * - Exponential backoff retry strategy
 * - Automatic cleanup of old notifications
 * - Metrics tracking for monitoring
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryService {

    private final NotificationRetryQueueRepository retryQueueRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final ObjectMapper objectMapper;

    /**
     * Queue notification for retry
     */
    @Transactional
    public void queueForRetry(NotificationRetryQueue notification) {
        notification.setStatus(NotificationRetryQueue.RetryStatus.PENDING);
        notification.calculateNextRetryTime();
        retryQueueRepository.save(notification);

        log.info("Queued notification for retry: type={}, recipientId={}, nextRetry={}",
            notification.getNotificationType(),
            notification.getRecipientId(),
            notification.getNextRetryAt());
    }

    /**
     * Queue email for retry
     */
    public void queueEmail(String recipientEmail, String subject, String body, String templateId, Object templateData) {
        NotificationRetryQueue notification = NotificationRetryQueue.builder()
            .notificationType(NotificationRetryQueue.NotificationType.EMAIL)
            .recipientEmail(recipientEmail)
            .subject(subject)
            .message(body)
            .templateId(templateId)
            .templateData(serializeTemplateData(templateData))
            .build();

        queueForRetry(notification);
    }

    /**
     * Queue SMS for retry
     */
    public void queueSms(String phoneNumber, String message) {
        NotificationRetryQueue notification = NotificationRetryQueue.builder()
            .notificationType(NotificationRetryQueue.NotificationType.SMS)
            .recipientPhone(phoneNumber)
            .message(message)
            .build();

        queueForRetry(notification);
    }

    /**
     * Queue push notification for retry
     */
    public void queuePushNotification(String userId, String title, String body) {
        NotificationRetryQueue notification = NotificationRetryQueue.builder()
            .notificationType(NotificationRetryQueue.NotificationType.PUSH_NOTIFICATION)
            .recipientId(userId)
            .subject(title)
            .message(body)
            .build();

        queueForRetry(notification);
    }

    /**
     * Process retry queue - runs every minute
     * Finds notifications ready for retry and attempts to send them
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000) // Every 60 seconds, start after 10 seconds
    @Transactional
    public void processRetryQueue() {
        List<NotificationRetryQueue> readyForRetry = retryQueueRepository.findReadyForRetry(LocalDateTime.now());

        if (readyForRetry.isEmpty()) {
            return;
        }

        log.info("Processing {} notifications from retry queue", readyForRetry.size());

        for (NotificationRetryQueue notification : readyForRetry) {
            try {
                retryNotification(notification);
            } catch (Exception e) {
                log.error("Failed to retry notification: {}", notification.getId(), e);
                handleRetryFailure(notification, e.getMessage());
            }
        }
    }

    /**
     * Attempt to retry a notification
     */
    private void retryNotification(NotificationRetryQueue notification) {
        log.debug("Retrying notification: id={}, type={}, attempt={}/{}",
            notification.getId(),
            notification.getNotificationType(),
            notification.getRetryCount() + 1,
            notification.getMaxRetryAttempts());

        boolean success = false;

        try {
            switch (notification.getNotificationType()) {
                case EMAIL:
                    success = retryEmail(notification);
                    break;
                case SMS:
                    success = retrySms(notification);
                    break;
                case PUSH_NOTIFICATION:
                    success = retryPushNotification(notification);
                    break;
                default:
                    log.warn("Unknown notification type: {}", notification.getNotificationType());
            }

            if (success) {
                notification.markAsCompleted();
                retryQueueRepository.save(notification);
                log.info("Successfully sent notification after {} retries: id={}",
                    notification.getRetryCount(), notification.getId());
            } else {
                handleRetryFailure(notification, "Service returned failure");
            }

        } catch (Exception e) {
            handleRetryFailure(notification, e.getMessage());
        }
    }

    /**
     * Retry email notification
     */
    private boolean retryEmail(NotificationRetryQueue notification) {
        try {
            // Call notification service
            notificationServiceClient.sendEmail(
                notification.getRecipientEmail(),
                notification.getSubject(),
                notification.getMessage()
            );
            return true;
        } catch (Exception e) {
            log.warn("Email retry failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Retry SMS notification
     */
    private boolean retrySms(NotificationRetryQueue notification) {
        try {
            notificationServiceClient.sendSms(
                notification.getRecipientPhone(),
                notification.getMessage()
            );
            return true;
        } catch (Exception e) {
            log.warn("SMS retry failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Retry push notification
     */
    private boolean retryPushNotification(NotificationRetryQueue notification) {
        try {
            notificationServiceClient.sendPushNotification(
                notification.getRecipientId(),
                notification.getSubject(),
                notification.getMessage()
            );
            return true;
        } catch (Exception e) {
            log.warn("Push notification retry failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Handle retry failure - increment retry count or mark as failed
     */
    private void handleRetryFailure(NotificationRetryQueue notification, String errorMessage) {
        notification.setLastError(errorMessage);
        notification.incrementRetry();
        retryQueueRepository.save(notification);

        if (notification.getStatus() == NotificationRetryQueue.RetryStatus.FAILED) {
            log.error("Notification failed after {} retries: id={}, type={}, error={}",
                notification.getMaxRetryAttempts(),
                notification.getId(),
                notification.getNotificationType(),
                errorMessage);
        } else {
            log.debug("Notification retry scheduled for {}: id={}",
                notification.getNextRetryAt(), notification.getId());
        }
    }

    /**
     * Cleanup old completed/failed notifications (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        int deleted = retryQueueRepository.deleteOldNotifications(cutoffDate);

        if (deleted > 0) {
            log.info("Cleaned up {} old notifications from retry queue", deleted);
        }
    }

    /**
     * Serialize template data to JSON
     */
    private String serializeTemplateData(Object templateData) {
        if (templateData == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(templateData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize template data", e);
            return null;
        }
    }
}
