package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(
    name = "notification-service",
    url = "${services.notification-service.url:http://notification-service:8080}",
    fallbackFactory = NotificationServiceClientFallbackFactory.class
)
public interface NotificationServiceClient {

    /**
     * Send reconciliation notification
     */
    @PostMapping("/api/v1/notifications/reconciliation")
    NotificationResult sendReconciliationNotification(@RequestBody ReconciliationNotificationRequest request);

    /**
     * Send break detection alert
     */
    @PostMapping("/api/v1/notifications/break-alert")
    NotificationResult sendBreakDetectionAlert(@RequestBody BreakDetectionAlertRequest request);

    /**
     * Send reconciliation completion notification
     */
    @PostMapping("/api/v1/notifications/reconciliation-complete")
    NotificationResult sendReconciliationCompleteNotification(@RequestBody ReconciliationCompleteRequest request);

    /**
     * Send escalation notification
     */
    @PostMapping("/api/v1/notifications/escalation")
    NotificationResult sendEscalationNotification(@RequestBody EscalationNotificationRequest request);

    /**
     * Send email notification
     */
    @PostMapping("/api/v1/notifications/email")
    NotificationResult sendEmailNotification(@RequestBody EmailNotificationRequest request);

    /**
     * Send SMS notification
     */
    @PostMapping("/api/v1/notifications/sms")
    NotificationResult sendSmsNotification(@RequestBody SmsNotificationRequest request);

    /**
     * Send Slack notification
     */
    @PostMapping("/api/v1/notifications/slack")
    NotificationResult sendSlackNotification(@RequestBody SlackNotificationRequest request);

    /**
     * Send webhook notification
     */
    @PostMapping("/api/v1/notifications/webhook")
    NotificationResult sendWebhookNotification(@RequestBody WebhookNotificationRequest request);

    /**
     * Get notification status
     */
    @GetMapping("/api/v1/notifications/{notificationId}/status")
    NotificationStatus getNotificationStatus(@PathVariable UUID notificationId);

    /**
     * Create notification template
     */
    @PostMapping("/api/v1/notifications/templates")
    NotificationTemplateResult createNotificationTemplate(@RequestBody NotificationTemplateRequest request);

    /**
     * Send scheduled notification
     */
    @PostMapping("/api/v1/notifications/scheduled")
    NotificationResult sendScheduledNotification(@RequestBody ScheduledNotificationRequest request);

    /**
     * Send bulk notifications
     */
    @PostMapping("/api/v1/notifications/bulk")
    BulkNotificationResult sendBulkNotifications(@RequestBody BulkNotificationRequest request);
}