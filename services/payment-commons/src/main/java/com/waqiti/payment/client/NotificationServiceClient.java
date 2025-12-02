package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.*;
import com.waqiti.payment.client.fallback.NotificationServiceFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified Notification Service Client for all payment services
 * Provides centralized notification capabilities with resilience patterns
 */
@FeignClient(
    name = "notification-service",
    path = "/api/v1/notifications",
    fallback = NotificationServiceFallback.class,
    configuration = NotificationServiceClientConfiguration.class
)
public interface NotificationServiceClient {

    // Email Notifications
    
    @PostMapping("/email/send")
    ResponseEntity<NotificationResponse> sendEmail(@RequestBody EmailNotificationRequest request);
    
    @PostMapping("/email/bulk")
    ResponseEntity<BulkNotificationResponse> sendBulkEmail(@RequestBody BulkEmailRequest request);
    
    @PostMapping("/email/template")
    ResponseEntity<NotificationResponse> sendTemplatedEmail(@RequestBody TemplatedEmailRequest request);

    // SMS Notifications
    
    @PostMapping("/sms/send")
    ResponseEntity<NotificationResponse> sendSMS(@RequestBody SMSNotificationRequest request);
    
    @PostMapping("/sms/bulk")
    ResponseEntity<BulkNotificationResponse> sendBulkSMS(@RequestBody BulkSMSRequest request);
    
    @PostMapping("/sms/otp")
    ResponseEntity<OTPResponse> sendOTP(@RequestBody OTPRequest request);
    
    @PostMapping("/sms/otp/verify")
    ResponseEntity<OTPVerificationResponse> verifyOTP(@RequestBody OTPVerificationRequest request);

    // Push Notifications
    
    @PostMapping("/push/send")
    ResponseEntity<NotificationResponse> sendPushNotification(@RequestBody PushNotificationRequest request);
    
    @PostMapping("/push/topic")
    ResponseEntity<NotificationResponse> sendToTopic(@RequestBody TopicNotificationRequest request);
    
    @PostMapping("/push/device/register")
    ResponseEntity<Void> registerDevice(@RequestBody DeviceRegistrationRequest request);
    
    @DeleteMapping("/push/device/{deviceToken}")
    ResponseEntity<Void> unregisterDevice(@PathVariable String deviceToken);

    // In-App Notifications
    
    @PostMapping("/in-app/send")
    ResponseEntity<NotificationResponse> sendInAppNotification(@RequestBody InAppNotificationRequest request);
    
    @GetMapping("/in-app/user/{userId}")
    ResponseEntity<List<InAppNotification>> getUserNotifications(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "false") boolean unreadOnly
    );
    
    @PutMapping("/in-app/{notificationId}/read")
    ResponseEntity<Void> markAsRead(@PathVariable UUID notificationId);
    
    @PutMapping("/in-app/user/{userId}/read-all")
    ResponseEntity<Void> markAllAsRead(@PathVariable UUID userId);

    // Payment-Specific Notifications
    
    @PostMapping("/payment/sent")
    ResponseEntity<NotificationResponse> sendPaymentSentNotification(
        @RequestParam UUID userId,
        @RequestParam UUID paymentId,
        @RequestParam BigDecimal amount,
        @RequestParam String currency,
        @RequestParam UUID recipientId
    );
    
    @PostMapping("/payment/received")
    ResponseEntity<NotificationResponse> sendPaymentReceivedNotification(
        @RequestParam UUID userId,
        @RequestParam UUID paymentId,
        @RequestParam BigDecimal amount,
        @RequestParam String currency,
        @RequestParam UUID senderId
    );
    
    @PostMapping("/payment/failed")
    ResponseEntity<NotificationResponse> sendPaymentFailedNotification(
        @RequestParam UUID userId,
        @RequestParam UUID paymentId,
        @RequestParam String reason
    );

    // Instant Transfer Notifications
    
    @PostMapping("/instant-transfer/sent")
    ResponseEntity<NotificationResponse> sendInstantTransferSentNotification(
        @RequestParam UUID userId,
        @RequestParam UUID transferId,
        @RequestParam BigDecimal amount,
        @RequestParam UUID recipientId
    );
    
    @PostMapping("/instant-transfer/received")
    ResponseEntity<NotificationResponse> sendInstantTransferReceivedNotification(
        @RequestParam UUID userId,
        @RequestParam UUID transferId,
        @RequestParam BigDecimal amount,
        @RequestParam UUID senderId
    );

    // Payment Request Notifications
    
    @PostMapping("/payment-request/created")
    ResponseEntity<NotificationResponse> sendPaymentRequestCreatedNotification(
        @RequestParam UUID recipientId,
        @RequestParam UUID requestId,
        @RequestParam BigDecimal amount,
        @RequestParam UUID requestorId
    );
    
    @PostMapping("/payment-request/approved")
    ResponseEntity<NotificationResponse> sendPaymentRequestApprovedNotification(
        @RequestParam UUID requestorId,
        @RequestParam UUID requestId,
        @RequestParam BigDecimal amount,
        @RequestParam UUID approverId
    );
    
    @PostMapping("/payment-request/rejected")
    ResponseEntity<NotificationResponse> sendPaymentRequestRejectedNotification(
        @RequestParam UUID requestorId,
        @RequestParam UUID requestId,
        @RequestParam UUID rejectorId
    );

    // Fraud and Security Alerts
    
    @PostMapping("/fraud/alert")
    ResponseEntity<NotificationResponse> sendFraudAlert(
        @RequestParam UUID transferId,
        @RequestBody FraudScore fraudScore
    );
    
    @PostMapping("/security/suspicious-activity")
    ResponseEntity<NotificationResponse> sendSuspiciousActivityAlert(
        @RequestParam UUID userId,
        @RequestParam String activityType,
        @RequestParam String description
    );
    
    @PostMapping("/security/login-alert")
    ResponseEntity<NotificationResponse> sendLoginAlert(
        @RequestParam UUID userId,
        @RequestParam String deviceInfo,
        @RequestParam String location
    );

    // Account Notifications
    
    @PostMapping("/account/low-balance")
    ResponseEntity<NotificationResponse> sendLowBalanceNotification(
        @RequestParam UUID userId,
        @RequestParam UUID walletId,
        @RequestParam BigDecimal currentBalance,
        @RequestParam BigDecimal threshold
    );
    
    @PostMapping("/account/limit-reached")
    ResponseEntity<NotificationResponse> sendLimitReachedNotification(
        @RequestParam UUID userId,
        @RequestParam String limitType,
        @RequestParam BigDecimal limitAmount
    );

    // Scheduled Notifications
    
    @PostMapping("/scheduled/create")
    ResponseEntity<ScheduledNotification> scheduleNotification(@RequestBody ScheduleNotificationRequest request);
    
    @DeleteMapping("/scheduled/{scheduledId}")
    ResponseEntity<Void> cancelScheduledNotification(@PathVariable UUID scheduledId);
    
    @GetMapping("/scheduled/user/{userId}")
    ResponseEntity<List<ScheduledNotification>> getUserScheduledNotifications(@PathVariable UUID userId);

    // Notification Preferences
    
    @GetMapping("/preferences/user/{userId}")
    ResponseEntity<NotificationPreferences> getUserPreferences(@PathVariable UUID userId);
    
    @PutMapping("/preferences/user/{userId}")
    ResponseEntity<NotificationPreferences> updateUserPreferences(
        @PathVariable UUID userId,
        @RequestBody NotificationPreferences preferences
    );
    
    @PostMapping("/preferences/user/{userId}/unsubscribe")
    ResponseEntity<Void> unsubscribeFromNotifications(
        @PathVariable UUID userId,
        @RequestParam String notificationType
    );

    // Notification History
    
    @GetMapping("/history/user/{userId}")
    ResponseEntity<List<NotificationHistory>> getUserNotificationHistory(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );
    
    @GetMapping("/history/stats/user/{userId}")
    ResponseEntity<NotificationStatistics> getUserNotificationStatistics(@PathVariable UUID userId);

    // Webhook Notifications
    
    @PostMapping("/webhook/register")
    ResponseEntity<WebhookRegistration> registerWebhook(@RequestBody WebhookRegistrationRequest request);
    
    @DeleteMapping("/webhook/{webhookId}")
    ResponseEntity<Void> unregisterWebhook(@PathVariable UUID webhookId);
    
    @PostMapping("/webhook/{webhookId}/test")
    ResponseEntity<WebhookTestResponse> testWebhook(@PathVariable UUID webhookId);

    // Batch Operations
    
    @PostMapping("/batch/send")
    ResponseEntity<BatchNotificationResponse> sendBatchNotifications(@RequestBody BatchNotificationRequest request);
    
    @GetMapping("/batch/{batchId}/status")
    ResponseEntity<BatchNotificationStatus> getBatchStatus(@PathVariable UUID batchId);
}