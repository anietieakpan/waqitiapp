package com.waqiti.reconciliation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Queue Service
 *
 * Queues failed notifications for retry when notification-service is unavailable.
 * Implements fire-and-forget pattern to ensure reconciliation operations are never
 * blocked by notification failures.
 *
 * QUEUE STRATEGY:
 * - Standard notifications → 3 retries with exponential backoff
 * - Urgent notifications → 5 retries with shorter backoff
 * - Critical notifications → Immediate alert + manual intervention
 *
 * @author Waqiti Reconciliation Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationQueueService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Queue notification for later retry
     */
    public void queueNotification(Object notificationRequest) {
        try {
            Map<String, Object> queuedNotification = new HashMap<>();
            queuedNotification.put("notification", notificationRequest);
            queuedNotification.put("queuedAt", LocalDateTime.now());
            queuedNotification.put("retryCount", 0);
            queuedNotification.put("maxRetries", 3);
            queuedNotification.put("priority", "NORMAL");

            kafkaTemplate.send("reconciliation-notification-retry-queue", queuedNotification);

            log.info("Notification queued for retry: type={}", notificationRequest.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Failed to queue notification for retry: {}", e.getMessage(), e);
            // Don't propagate - notification queuing is fire-and-forget
        }
    }

    /**
     * Queue urgent notification with higher priority retry
     */
    public void queueUrgentNotification(Object notificationRequest) {
        try {
            Map<String, Object> queuedNotification = new HashMap<>();
            queuedNotification.put("notification", notificationRequest);
            queuedNotification.put("queuedAt", LocalDateTime.now());
            queuedNotification.put("retryCount", 0);
            queuedNotification.put("maxRetries", 5);
            queuedNotification.put("priority", "URGENT");

            kafkaTemplate.send("reconciliation-notification-urgent-queue", queuedNotification);

            log.warn("URGENT notification queued for retry: type={}", notificationRequest.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to queue URGENT notification for retry: {}", e.getMessage(), e);
            // Log to DLQ for manual intervention
            logToDeadLetterQueue("URGENT_NOTIFICATION_QUEUE_FAILURE", notificationRequest, e);
        }
    }

    /**
     * Queue bulk notifications for later retry
     */
    public void queueBulkNotifications(Object bulkRequest) {
        try {
            Map<String, Object> queuedBulk = new HashMap<>();
            queuedBulk.put("bulkRequest", bulkRequest);
            queuedBulk.put("queuedAt", LocalDateTime.now());
            queuedBulk.put("retryCount", 0);
            queuedBulk.put("maxRetries", 3);

            kafkaTemplate.send("reconciliation-notification-bulk-retry-queue", queuedBulk);

            log.info("Bulk notifications queued for retry");

        } catch (Exception e) {
            log.error("Failed to queue bulk notifications for retry: {}", e.getMessage(), e);
        }
    }

    /**
     * Send to dead letter queue for manual intervention
     */
    private void logToDeadLetterQueue(String failureType, Object request, Exception error) {
        try {
            Map<String, Object> dlqEntry = new HashMap<>();
            dlqEntry.put("failureType", failureType);
            dlqEntry.put("request", request);
            dlqEntry.put("error", error.getMessage());
            dlqEntry.put("timestamp", LocalDateTime.now());
            dlqEntry.put("requiresManualIntervention", true);

            kafkaTemplate.send("reconciliation-notification-dlq", dlqEntry);

            log.error("Sent to DLQ for manual intervention: failureType={}", failureType);

        } catch (Exception dlqError) {
            log.error("CATASTROPHIC: Failed to send to DLQ: {}", dlqError.getMessage(), dlqError);
            // At this point, we can only log - the notification is lost
        }
    }
}
