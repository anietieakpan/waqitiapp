package com.waqiti.common.notification;

import com.waqiti.common.notification.model.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive notification service interface for multi-channel notifications
 * Supports email, SMS, push notifications, in-app notifications, and webhooks
 */
public interface NotificationService {
    
    /**
     * Send an email notification with full configuration
     */
    CompletableFuture<NotificationResult> sendEmail(EmailNotificationRequest request);
    
    /**
     * Send an SMS notification with delivery tracking
     */
    CompletableFuture<NotificationResult> sendSms(SmsNotificationRequest request);
    
    /**
     * Send a push notification to mobile devices
     */
    CompletableFuture<NotificationResult> sendPushNotification(PushNotificationRequest request);
    
    /**
     * Send an in-app notification
     */
    CompletableFuture<NotificationResult> sendInAppNotification(InAppNotificationRequest request);

    /**
     * Send a Slack notification to channels or users
     */
    CompletableFuture<NotificationResult> sendSlack(SlackNotificationRequest request);

    /**
     * Send a webhook notification
     */
    CompletableFuture<NotificationResult> sendWebhook(WebhookNotificationRequest request);
    
    /**
     * Send a multi-channel notification
     */
    CompletableFuture<MultiChannelNotificationResult> sendMultiChannel(MultiChannelNotificationRequest request);
    
    /**
     * Send a batch of notifications
     */
    CompletableFuture<BatchNotificationResult> sendBatch(BatchNotificationRequest request);
    
    /**
     * Send a templated notification
     */
    CompletableFuture<NotificationResult> sendTemplated(TemplatedNotificationRequest request);
    
    /**
     * Schedule a notification for future delivery
     */
    CompletableFuture<ScheduledNotificationResult> scheduleNotification(ScheduledNotificationRequest request);
    
    /**
     * Cancel a scheduled notification
     */
    CompletableFuture<Void> cancelScheduledNotification(String notificationId);
    
    /**
     * Get notification status
     */
    CompletableFuture<NotificationStatus> getNotificationStatus(String notificationId);
    
    /**
     * Get notification delivery report
     */
    CompletableFuture<DeliveryReport> getDeliveryReport(String notificationId);
    
    /**
     * Get user notification preferences
     */
    CompletableFuture<NotificationPreferences> getUserPreferences(String userId);
    
    /**
     * Update user notification preferences
     */
    CompletableFuture<NotificationPreferences> updateUserPreferences(String userId, NotificationPreferences preferences);
    
    /**
     * Send a critical alert to operations team
     */
    CompletableFuture<NotificationResult> sendCriticalAlert(CriticalAlertRequest request);
    
    /**
     * Send a security alert
     */
    CompletableFuture<NotificationResult> sendSecurityAlert(SecurityAlertRequest request);
    
    /**
     * Send a compliance notification
     */
    CompletableFuture<NotificationResult> sendComplianceNotification(ComplianceNotificationRequest request);
    
    /**
     * Get notification analytics
     */
    CompletableFuture<NotificationAnalytics> getAnalytics(AnalyticsRequest request);
    
    /**
     * Validate notification request
     */
    ValidationResult validateNotificationRequest(NotificationRequest request);
    
    /**
     * Register a notification template
     */
    CompletableFuture<TemplateRegistrationResult> registerTemplate(NotificationTemplate template);
    
    /**
     * Update a notification template
     */
    CompletableFuture<TemplateRegistrationResult> updateTemplate(String templateId, NotificationTemplate template);
    
    /**
     * Delete a notification template
     */
    CompletableFuture<Void> deleteTemplate(String templateId);
    
    /**
     * Get notification template
     */
    CompletableFuture<NotificationTemplate> getTemplate(String templateId);
    
    /**
     * List notification templates
     */
    CompletableFuture<List<NotificationTemplate>> listTemplates(TemplateFilter filter);
    
    /**
     * Subscribe to notification events
     */
    CompletableFuture<SubscriptionResult> subscribeToEvents(NotificationEventSubscription subscription);
    
    /**
     * Unsubscribe from notification events
     */
    CompletableFuture<Void> unsubscribeFromEvents(String subscriptionId);
    
    /**
     * Get notification history for a user
     */
    CompletableFuture<NotificationHistory> getUserNotificationHistory(String userId, HistoryFilter filter);
    
    /**
     * Mark notification as read
     */
    CompletableFuture<Void> markAsRead(String userId, String notificationId);
    
    /**
     * Mark all notifications as read
     */
    CompletableFuture<Void> markAllAsRead(String userId);
    
    /**
     * Delete notification
     */
    CompletableFuture<Void> deleteNotification(String userId, String notificationId);
    
    /**
     * Get unread notification count
     */
    CompletableFuture<UnreadCount> getUnreadCount(String userId);
    
    /**
     * Test notification configuration
     */
    CompletableFuture<TestResult> testConfiguration(NotificationChannel channel, Map<String, Object> config);
    
    /**
     * Send notification when incident is created
     */
    CompletableFuture<NotificationResult> sendIncidentCreated(com.waqiti.common.model.incident.Incident incident);

    /**
     * Send notification when incident is assigned
     */
    CompletableFuture<NotificationResult> sendIncidentAssigned(com.waqiti.common.model.incident.Incident incident, String assignedTo);

    /**
     * Send notification when incident is resolved
     */
    CompletableFuture<NotificationResult> sendIncidentResolved(com.waqiti.common.model.incident.Incident incident);

    /**
     * Send an urgent notification using the fastest available channel (rich object version)
     * PRODUCTION FIX: Primary overload that accepts full NotificationRequest
     * This is the canonical implementation - simple version delegates to this
     *
     * @param request Notification request with full configuration
     * @return Future notification result
     */
    default CompletableFuture<NotificationResult> sendUrgentNotification(NotificationRequest request) {
        // Route based on request type
        if (request instanceof EmailNotificationRequest) {
            return sendEmail((EmailNotificationRequest) request);
        } else if (request instanceof SmsNotificationRequest) {
            return sendSms((SmsNotificationRequest) request);
        } else if (request instanceof PushNotificationRequest) {
            return sendPushNotification((PushNotificationRequest) request);
        } else if (request instanceof StandardNotificationRequest) {
            StandardNotificationRequest stdRequest = (StandardNotificationRequest) request;
            // Convert to email as default urgent channel
            EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                .userId(request.getUserId())
                .subject(stdRequest.getSubject())
                .textContent(stdRequest.getMessage())
                .priority(request.getPriority() != null ? request.getPriority() : NotificationRequest.Priority.CRITICAL)
                .metadata(request.getMetadata())
                .correlationId(request.getCorrelationId())
                .build();
            return sendEmail(emailRequest);
        } else {
            // Generic fallback for unknown types
            EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                .userId(request.getUserId())
                .subject("Urgent Notification")
                .textContent("Urgent notification - please check system")
                .priority(NotificationRequest.Priority.CRITICAL)
                .build();
            return sendEmail(emailRequest);
        }
    }

    /**
     * Send an urgent notification using the fastest available channel (convenience method)
     * This is a simplified method for critical alerts
     *
     * @param userId User ID to notify
     * @param subject Notification subject
     * @param message Notification message
     * @return Future notification result
     */
    default CompletableFuture<NotificationResult> sendUrgentNotification(String userId, String subject, String message) {
        // Delegate to rich object version
        StandardNotificationRequest request = StandardNotificationRequest.builder()
            .userId(userId)
            .recipient(userId)
            .subject(subject)
            .message(message)
            .priority(NotificationRequest.Priority.CRITICAL)
            .build();

        return sendUrgentNotification(request);
    }

    /**
     * Send engineering alert for technical issues
     */
    default CompletableFuture<NotificationResult> sendEngineeringAlert(String title, String message) {
        CriticalAlertRequest request = CriticalAlertRequest.builder()
            .title(title)
            .message(message)
            .severity(CriticalAlertRequest.AlertSeverity.HIGH)
            .source("Engineering")
            .build();
        return sendCriticalAlert(request);
    }

    /**
     * Send operational alert for operations team
     */
    default CompletableFuture<NotificationResult> sendOperationalAlert(String title, String message, String severity) {
        CriticalAlertRequest request = CriticalAlertRequest.builder()
            .title(title)
            .message(message)
            .severity(CriticalAlertRequest.AlertSeverity.valueOf(severity.toUpperCase()))
            .source("Operations")
            .build();
        return sendCriticalAlert(request);
    }

    /**
     * Send management alert for executive attention
     */
    default CompletableFuture<NotificationResult> sendManagementAlert(String title, String message) {
        CriticalAlertRequest request = CriticalAlertRequest.builder()
            .title(title)
            .message(message)
            .severity(CriticalAlertRequest.AlertSeverity.CRITICAL)
            .source("Management")
            .build();
        return sendCriticalAlert(request);
    }

    /**
     * Send a simple notification (rich object version)
     * PRODUCTION FIX: Primary overload that accepts full NotificationRequest
     */
    default CompletableFuture<NotificationResult> sendNotification(NotificationRequest request) {
        return sendUrgentNotification(request); // Reuse the routing logic
    }

    /**
     * Send a simple notification (convenience method)
     */
    void sendNotification(String userId, String title, String message, String correlationId);

    /**
     * Send batch completion notification
     */
    void sendBatchCompletionNotification(String requestedBy, Map<String, Object> notificationData);

    /**
     * Send merchant notification
     */
    void sendMerchantNotification(String merchantId, String notificationType, Map<String, Object> notificationData);

    void sendAccountOpsAlert(String alertTitle, String alertMessage, String critical); // TODO - what does this do? check if this is production ready - added by aniix

    void sendKycAlert(String kycAccountCreationFailed, String format, String high); // TODO - what does this do? check if this is production ready - added by aniix

    void sendOnboardingAlert(String s, String alertMessage, String high); // TODO - what does this do? check if this is production ready - added by aniix

    void sendBusinessAccountAlert(String businessAccountCreationFailed, String format, String high); // TODO - what does this do? check if this is production ready - added by aniix

    void sendComplianceAlert(String regulatoryAccountCreationFailed, String format, String high); // TODO - what does this do? check if this is production ready - added by aniix

    void sendRiskManagementAlert(String accountCreationRiskAlert, String format, String medium); // TODO - what does this do? check if this is production ready - added by aniix

    void sendOfacAlert(String ofacScreeningAccountFailed, String format, String high); // TODO - what does this do? check if this is production ready - added by aniix

    void sendCustomerServiceAlert(String s, String format, String medium); // TODO - what does this do? check if this is production ready - added by aniix

    void sendAmlAlert(String s, String format, String high); // TODO - what does this do? check if this is production ready - added by aniix

    /**
     * PRODUCTION FIX: Send simple alert with title and message
     * Used by SystemAlertsService
     */
    default CompletableFuture<NotificationResult> sendAlert(String title, String message) {
        return sendEngineeringAlert(title, message);
    }
}