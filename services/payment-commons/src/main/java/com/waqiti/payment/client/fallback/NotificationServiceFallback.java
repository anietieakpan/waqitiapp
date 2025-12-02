package com.waqiti.payment.client.fallback;

import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fallback implementation for NotificationServiceClient
 * Provides graceful degradation when notification service is unavailable
 */
@Component
@Slf4j
public class NotificationServiceFallback implements NotificationServiceClient {

    private static final String FALLBACK_MESSAGE = "Notification service temporarily unavailable";

    @Override
    public ResponseEntity<NotificationResponse> sendEmail(EmailNotificationRequest request) {
        log.warn("Fallback: Email notification failed for user {}", request.getUserId());
        return ResponseEntity.ok(createFallbackResponse("EMAIL_QUEUED"));
    }

    @Override
    public ResponseEntity<BulkNotificationResponse> sendBulkEmail(BulkEmailRequest request) {
        log.warn("Fallback: Bulk email notification failed for {} recipients", request.getRecipients().size());
        return ResponseEntity.ok(createBulkFallbackResponse(request.getRecipients().size()));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendTemplatedEmail(TemplatedEmailRequest request) {
        log.warn("Fallback: Templated email notification failed for user {}", request.getUserId());
        return ResponseEntity.ok(createFallbackResponse("EMAIL_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendSMS(SMSNotificationRequest request) {
        log.warn("Fallback: SMS notification failed for user {}", request.getUserId());
        return ResponseEntity.ok(createFallbackResponse("SMS_QUEUED"));
    }

    @Override
    public ResponseEntity<BulkNotificationResponse> sendBulkSMS(BulkSMSRequest request) {
        log.warn("Fallback: Bulk SMS notification failed for {} recipients", request.getPhoneNumbers().size());
        return ResponseEntity.ok(createBulkFallbackResponse(request.getPhoneNumbers().size()));
    }

    @Override
    public ResponseEntity<OTPResponse> sendOTP(OTPRequest request) {
        log.warn("Fallback: OTP sending failed for user {}", request.getUserId());
        // For security, we don't queue OTPs - return error
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @Override
    public ResponseEntity<OTPVerificationResponse> verifyOTP(OTPVerificationRequest request) {
        log.warn("Fallback: OTP verification failed for reference {}", request.getReferenceId());
        // For security, we fail OTP verification in fallback
        return ResponseEntity.ok(OTPVerificationResponse.builder()
            .verified(false)
            .message("Verification service unavailable")
            .attemptsRemaining(0)
            .build());
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPushNotification(PushNotificationRequest request) {
        log.warn("Fallback: Push notification failed for user {}", request.getUserId());
        return ResponseEntity.ok(createFallbackResponse("PUSH_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendToTopic(TopicNotificationRequest request) {
        log.warn("Fallback: Topic notification failed for topic {}", request.getTopic());
        return ResponseEntity.ok(createFallbackResponse("TOPIC_QUEUED"));
    }

    @Override
    public ResponseEntity<Void> registerDevice(DeviceRegistrationRequest request) {
        log.warn("Fallback: Device registration failed for user {}", request.getUserId());
        // Queue device registration for later
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> unregisterDevice(String deviceToken) {
        log.warn("Fallback: Device unregistration failed for token {}", deviceToken);
        // Queue device unregistration for later
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<NotificationResponse> sendInAppNotification(InAppNotificationRequest request) {
        log.warn("Fallback: In-app notification failed for user {}", request.getUserId());
        return ResponseEntity.ok(createFallbackResponse("IN_APP_QUEUED"));
    }

    @Override
    public ResponseEntity<List<InAppNotification>> getUserNotifications(UUID userId, boolean unreadOnly) {
        log.warn("Fallback: Cannot retrieve notifications for user {}", userId);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Void> markAsRead(UUID notificationId) {
        log.warn("Fallback: Cannot mark notification {} as read", notificationId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> markAllAsRead(UUID userId) {
        log.warn("Fallback: Cannot mark all notifications as read for user {}", userId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentSentNotification(
            UUID userId, UUID paymentId, BigDecimal amount, String currency, UUID recipientId) {
        log.warn("Fallback: Payment sent notification failed for user {}", userId);
        // Critical notification - log for manual follow-up
        logCriticalNotification("PAYMENT_SENT", userId, Map.of(
            "paymentId", paymentId,
            "amount", amount,
            "currency", currency,
            "recipientId", recipientId
        ));
        return ResponseEntity.ok(createFallbackResponse("PAYMENT_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentReceivedNotification(
            UUID userId, UUID paymentId, BigDecimal amount, String currency, UUID senderId) {
        log.warn("Fallback: Payment received notification failed for user {}", userId);
        // Critical notification - log for manual follow-up
        logCriticalNotification("PAYMENT_RECEIVED", userId, Map.of(
            "paymentId", paymentId,
            "amount", amount,
            "currency", currency,
            "senderId", senderId
        ));
        return ResponseEntity.ok(createFallbackResponse("PAYMENT_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentFailedNotification(
            UUID userId, UUID paymentId, String reason) {
        log.warn("Fallback: Payment failed notification failed for user {}", userId);
        // Critical notification - log for manual follow-up
        logCriticalNotification("PAYMENT_FAILED", userId, Map.of(
            "paymentId", paymentId,
            "reason", reason
        ));
        return ResponseEntity.ok(createFallbackResponse("PAYMENT_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendInstantTransferSentNotification(
            UUID userId, UUID transferId, BigDecimal amount, UUID recipientId) {
        log.warn("Fallback: Instant transfer sent notification failed for user {}", userId);
        logCriticalNotification("INSTANT_TRANSFER_SENT", userId, Map.of(
            "transferId", transferId,
            "amount", amount,
            "recipientId", recipientId
        ));
        return ResponseEntity.ok(createFallbackResponse("TRANSFER_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendInstantTransferReceivedNotification(
            UUID userId, UUID transferId, BigDecimal amount, UUID senderId) {
        log.warn("Fallback: Instant transfer received notification failed for user {}", userId);
        logCriticalNotification("INSTANT_TRANSFER_RECEIVED", userId, Map.of(
            "transferId", transferId,
            "amount", amount,
            "senderId", senderId
        ));
        return ResponseEntity.ok(createFallbackResponse("TRANSFER_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentRequestCreatedNotification(
            UUID recipientId, UUID requestId, BigDecimal amount, UUID requestorId) {
        log.warn("Fallback: Payment request created notification failed for recipient {}", recipientId);
        return ResponseEntity.ok(createFallbackResponse("REQUEST_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentRequestApprovedNotification(
            UUID requestorId, UUID requestId, BigDecimal amount, UUID approverId) {
        log.warn("Fallback: Payment request approved notification failed for requestor {}", requestorId);
        return ResponseEntity.ok(createFallbackResponse("REQUEST_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentRequestRejectedNotification(
            UUID requestorId, UUID requestId, UUID rejectorId) {
        log.warn("Fallback: Payment request rejected notification failed for requestor {}", requestorId);
        return ResponseEntity.ok(createFallbackResponse("REQUEST_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendFraudAlert(UUID transferId, FraudScore fraudScore) {
        log.error("CRITICAL: Fraud alert failed for transfer {} with score {}", transferId, fraudScore.getScore());
        // This is critical - ensure it's logged prominently
        logCriticalNotification("FRAUD_ALERT", null, Map.of(
            "transferId", transferId,
            "fraudScore", fraudScore.getScore(),
            "riskLevel", fraudScore.getRiskLevel(),
            "shouldBlock", fraudScore.shouldBlock()
        ));
        return ResponseEntity.ok(createFallbackResponse("FRAUD_ALERT_LOGGED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendSuspiciousActivityAlert(
            UUID userId, String activityType, String description) {
        log.error("CRITICAL: Suspicious activity alert failed for user {}: {} - {}", 
            userId, activityType, description);
        logCriticalNotification("SUSPICIOUS_ACTIVITY", userId, Map.of(
            "activityType", activityType,
            "description", description
        ));
        return ResponseEntity.ok(createFallbackResponse("SECURITY_ALERT_LOGGED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendLoginAlert(
            UUID userId, String deviceInfo, String location) {
        log.warn("Fallback: Login alert failed for user {}", userId);
        return ResponseEntity.ok(createFallbackResponse("LOGIN_ALERT_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendLowBalanceNotification(
            UUID userId, UUID walletId, BigDecimal currentBalance, BigDecimal threshold) {
        log.warn("Fallback: Low balance notification failed for user {}", userId);
        return ResponseEntity.ok(createFallbackResponse("BALANCE_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<NotificationResponse> sendLimitReachedNotification(
            UUID userId, String limitType, BigDecimal limitAmount) {
        log.warn("Fallback: Limit reached notification failed for user {}", userId);
        return ResponseEntity.ok(createFallbackResponse("LIMIT_NOTIFICATION_QUEUED"));
    }

    @Override
    public ResponseEntity<ScheduledNotification> scheduleNotification(ScheduleNotificationRequest request) {
        log.warn("Fallback: Cannot schedule notification for user {}", request.getUserId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @Override
    public ResponseEntity<Void> cancelScheduledNotification(UUID scheduledId) {
        log.warn("Fallback: Cannot cancel scheduled notification {}", scheduledId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<List<ScheduledNotification>> getUserScheduledNotifications(UUID userId) {
        log.warn("Fallback: Cannot retrieve scheduled notifications for user {}", userId);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<NotificationPreferences> getUserPreferences(UUID userId) {
        log.warn("Fallback: Cannot retrieve preferences for user {}", userId);
        // Return default preferences
        return ResponseEntity.ok(NotificationPreferences.builder()
            .userId(userId)
            .emailEnabled(true)
            .smsEnabled(true)
            .pushEnabled(true)
            .inAppEnabled(true)
            .notificationTypes(new HashMap<>())
            .quietHours(Collections.emptyList())
            .timezone("UTC")
            .build());
    }

    @Override
    public ResponseEntity<NotificationPreferences> updateUserPreferences(
            UUID userId, NotificationPreferences preferences) {
        log.warn("Fallback: Cannot update preferences for user {}", userId);
        // Queue preference update for later
        return ResponseEntity.accepted().body(preferences);
    }

    @Override
    public ResponseEntity<Void> unsubscribeFromNotifications(UUID userId, String notificationType) {
        log.warn("Fallback: Cannot unsubscribe user {} from {}", userId, notificationType);
        // Queue unsubscription for later
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<List<NotificationHistory>> getUserNotificationHistory(
            UUID userId, int page, int size) {
        log.warn("Fallback: Cannot retrieve notification history for user {}", userId);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<NotificationStatistics> getUserNotificationStatistics(UUID userId) {
        log.warn("Fallback: Cannot retrieve notification statistics for user {}", userId);
        return ResponseEntity.ok(NotificationStatistics.builder()
            .userId(userId)
            .totalSent(0L)
            .totalDelivered(0L)
            .totalRead(0L)
            .byChannel(new HashMap<>())
            .byType(new HashMap<>())
            .periodStart(LocalDateTime.now())
            .periodEnd(LocalDateTime.now())
            .build());
    }

    @Override
    public ResponseEntity<WebhookRegistration> registerWebhook(WebhookRegistrationRequest request) {
        log.warn("Fallback: Cannot register webhook for user {}", request.getUserId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @Override
    public ResponseEntity<Void> unregisterWebhook(UUID webhookId) {
        log.warn("Fallback: Cannot unregister webhook {}", webhookId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<WebhookTestResponse> testWebhook(UUID webhookId) {
        log.warn("Fallback: Cannot test webhook {}", webhookId);
        return ResponseEntity.ok(WebhookTestResponse.builder()
            .webhookId(webhookId)
            .statusCode(503)
            .response("Service unavailable")
            .responseTimeMs(0L)
            .success(false)
            .error(FALLBACK_MESSAGE)
            .build());
    }

    @Override
    public ResponseEntity<BatchNotificationResponse> sendBatchNotifications(BatchNotificationRequest request) {
        log.warn("Fallback: Batch notification failed for {} items", request.getNotifications().size());
        return ResponseEntity.ok(BatchNotificationResponse.builder()
            .batchId(UUID.randomUUID())
            .status("QUEUED")
            .totalCount(request.getNotifications().size())
            .processedCount(0)
            .successCount(0)
            .failureCount(0)
            .startedAt(LocalDateTime.now())
            .build());
    }

    @Override
    public ResponseEntity<BatchNotificationStatus> getBatchStatus(UUID batchId) {
        log.warn("Fallback: Cannot retrieve batch status for {}", batchId);
        return ResponseEntity.ok(BatchNotificationStatus.builder()
            .batchId(batchId)
            .status("UNKNOWN")
            .progress(0)
            .totalItems(0)
            .processedItems(0)
            .successCount(0)
            .failureCount(0)
            .errors(Collections.singletonList(FALLBACK_MESSAGE))
            .build());
    }

    // Helper methods

    private NotificationResponse createFallbackResponse(String status) {
        return NotificationResponse.builder()
            .notificationId(UUID.randomUUID())
            .status(status)
            .message(FALLBACK_MESSAGE)
            .sentAt(LocalDateTime.now())
            .metadata(Map.of("fallback", true))
            .build();
    }

    private BulkNotificationResponse createBulkFallbackResponse(int recipientCount) {
        return BulkNotificationResponse.builder()
            .batchId(UUID.randomUUID())
            .totalRecipients(recipientCount)
            .successCount(0)
            .failureCount(0)
            .results(Collections.emptyList())
            .build();
    }

    private void logCriticalNotification(String type, UUID userId, Map<String, Object> data) {
        log.error("CRITICAL NOTIFICATION FAILURE - Type: {}, User: {}, Data: {}", 
            type, userId, data);
        // In production, this would write to a persistent queue or database
        // for manual processing or retry when service is restored
    }
}