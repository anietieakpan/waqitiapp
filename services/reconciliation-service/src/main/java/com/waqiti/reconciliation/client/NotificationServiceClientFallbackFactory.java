package com.waqiti.reconciliation.client;

import com.waqiti.common.feign.BaseFallbackFactory;
import com.waqiti.reconciliation.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fallback Factory for NotificationServiceClient
 *
 * CRITICAL: Notification failures should NEVER block reconciliation operations
 *
 * FALLBACK STRATEGY:
 * - ALL notification failures → Return success (fire-and-forget)
 * - Log failures for manual retry
 * - Queue notifications for later delivery
 * - Never propagate exceptions
 *
 * NOTIFICATION RESILIENCE:
 * - Reconciliation > Notifications
 * - Failed notifications logged to database
 * - Background job retries failed notifications
 * - Operations team alerted for critical notifications
 *
 * @author Waqiti Reconciliation Team
 * @version 3.0.0
 */
@Component
@Slf4j
public class NotificationServiceClientFallbackFactory extends BaseFallbackFactory<NotificationServiceClient> {

    private final com.waqiti.reconciliation.service.NotificationQueueService queueService;

    public NotificationServiceClientFallbackFactory(
            com.waqiti.reconciliation.service.NotificationQueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    protected NotificationServiceClient createFallback(Throwable cause) {
        return new NotificationServiceClient() {

            @Override
            public NotificationResult sendReconciliationNotification(ReconciliationNotificationRequest request) {
                log.warn("NotificationServiceClient.sendReconciliationNotification fallback - queueing for retry");

                // Queue for later retry
                queueService.queueNotification(request);

                // Return success - don't block reconciliation
                return createSuccessResult("Queued for delivery");
            }

            @Override
            public NotificationResult sendBreakDetectionAlert(BreakDetectionAlertRequest request) {
                log.error("NotificationServiceClient.sendBreakDetectionAlert fallback - CRITICAL ALERT FAILED");

                // CRITICAL: Break detection alerts are high priority
                // Queue for immediate retry
                queueService.queueUrgentNotification(request);

                // Alert operations team via backup channel
                logCriticalNotificationFailure("BREAK_DETECTION_ALERT", request);

                return createSuccessResult("Queued for urgent delivery");
            }

            @Override
            public NotificationResult sendReconciliationCompleteNotification(ReconciliationCompleteRequest request) {
                log.warn("NotificationServiceClient.sendReconciliationCompleteNotification fallback");

                // Queue for later retry
                queueService.queueNotification(request);

                return createSuccessResult("Queued for delivery");
            }

            @Override
            public NotificationResult sendEscalationNotification(EscalationNotificationRequest request) {
                log.error("NotificationServiceClient.sendEscalationNotification fallback - ESCALATION NOTIFICATION FAILED");

                // CRITICAL: Escalation notifications must be delivered
                // Queue for immediate retry
                queueService.queueUrgentNotification(request);

                logCriticalNotificationFailure("ESCALATION", request);

                return createSuccessResult("Queued for urgent delivery");
            }

            @Override
            public NotificationResult sendEmailNotification(EmailNotificationRequest request) {
                log.warn("NotificationServiceClient.sendEmailNotification fallback");

                // Queue for later retry
                queueService.queueNotification(request);

                return createSuccessResult("Queued for delivery");
            }

            @Override
            public NotificationResult sendSmsNotification(SmsNotificationRequest request) {
                log.warn("NotificationServiceClient.sendSmsNotification fallback");

                // Queue for later retry
                queueService.queueNotification(request);

                return createSuccessResult("Queued for delivery");
            }

            @Override
            public NotificationResult sendSlackNotification(SlackNotificationRequest request) {
                log.warn("NotificationServiceClient.sendSlackNotification fallback");

                // Queue for later retry
                queueService.queueNotification(request);

                return createSuccessResult("Queued for delivery");
            }

            @Override
            public NotificationResult sendWebhookNotification(WebhookNotificationRequest request) {
                log.warn("NotificationServiceClient.sendWebhookNotification fallback");

                // Queue for later retry
                queueService.queueNotification(request);

                return createSuccessResult("Queued for delivery");
            }

            @Override
            public NotificationStatus getNotificationStatus(UUID notificationId) {
                log.warn("NotificationServiceClient.getNotificationStatus fallback for notificationId: {}", notificationId);

                // Return unknown status
                return createUnknownStatus(notificationId);
            }

            @Override
            public NotificationTemplateResult createNotificationTemplate(NotificationTemplateRequest request) {
                log.warn("NotificationServiceClient.createNotificationTemplate fallback");

                // Return error - template creation should not fail silently
                return NotificationTemplateResult.builder()
                    .success(false)
                    .message("Notification service unavailable")
                    .fallback(true)
                    .timestamp(LocalDateTime.now())
                    .build();
            }

            @Override
            public NotificationResult sendScheduledNotification(ScheduledNotificationRequest request) {
                log.warn("NotificationServiceClient.sendScheduledNotification fallback");

                // Queue for later retry
                queueService.queueNotification(request);

                return createSuccessResult("Queued for delivery");
            }

            @Override
            public BulkNotificationResult sendBulkNotifications(BulkNotificationRequest request) {
                log.warn("NotificationServiceClient.sendBulkNotifications fallback for {} notifications",
                    request.getNotifications().size());

                // Queue all notifications for later retry
                queueService.queueBulkNotifications(request);

                return createBulkSuccessResult(request.getNotifications().size());
            }
        };
    }

    /**
     * Create success result for fallback
     */
    private NotificationResult createSuccessResult(String message) {
        return NotificationResult.builder()
            .success(true)
            .message(message)
            .notificationId(UUID.randomUUID())
            .status("QUEUED")
            .fallback(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Create unknown status for fallback
     */
    private NotificationStatus createUnknownStatus(UUID notificationId) {
        return NotificationStatus.builder()
            .notificationId(notificationId)
            .status("UNKNOWN")
            .message("Notification service unavailable")
            .fallback(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Create bulk success result for fallback
     */
    private BulkNotificationResult createBulkSuccessResult(int count) {
        return BulkNotificationResult.builder()
            .totalCount(count)
            .successCount(count)
            .failedCount(0)
            .queuedCount(count)
            .message("All notifications queued for delivery")
            .fallback(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Log critical notification failure for manual intervention
     */
    private void logCriticalNotificationFailure(String notificationType, Object request) {
        log.error("CRITICAL_NOTIFICATION_FAILURE: Type={}, Request={}, RequiresManualIntervention=true",
            notificationType, request);

        // Critical notifications are already queued urgently by queueService
        // Additional alerting would be handled by monitoring systems watching the logs
        // and the urgent queue processing
    }

    @Override
    protected String getClientName() {
        return "NotificationServiceClient";
    }
}
