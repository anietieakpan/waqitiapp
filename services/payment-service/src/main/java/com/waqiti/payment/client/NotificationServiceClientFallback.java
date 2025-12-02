package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for NotificationServiceClient
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public ResponseEntity<NotificationResponse> sendPaymentNotification(PaymentNotificationRequest request) {
        log.warn("NotificationService fallback: sendPaymentNotification for user: {}", request.getUserId());
        return ResponseEntity.ok(NotificationResponse.builder()
                .success(false)
                .message("Notification service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<NotificationResponse> sendP2PNotification(P2PNotificationRequest request) {
        log.warn("NotificationService fallback: sendP2PNotification for sender: {}", request.getSenderId());
        return ResponseEntity.ok(NotificationResponse.builder()
                .success(false)
                .message("Notification service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<BulkNotificationResponse> sendBulkNotifications(BulkNotificationRequest request) {
        log.warn("NotificationService fallback: sendBulkNotifications for {} recipients", 
                request.getRecipients() != null ? request.getRecipients().size() : 0);
        return ResponseEntity.ok(BulkNotificationResponse.builder()
                .totalRequested(request.getRecipients() != null ? request.getRecipients().size() : 0)
                .totalSent(0)
                .totalFailed(request.getRecipients() != null ? request.getRecipients().size() : 0)
                .build());
    }

    @Override
    public ResponseEntity<NotificationResponse> sendSmsNotification(SmsNotificationRequest request) {
        log.warn("NotificationService fallback: sendSmsNotification to: {}", request.getPhoneNumber());
        return ResponseEntity.ok(NotificationResponse.builder()
                .success(false)
                .message("Notification service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<NotificationResponse> sendEmailNotification(EmailNotificationRequest request) {
        log.warn("NotificationService fallback: sendEmailNotification to: {}", request.getEmail());
        return ResponseEntity.ok(NotificationResponse.builder()
                .success(false)
                .message("Notification service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<NotificationResponse> sendPushNotification(PushNotificationRequest request) {
        log.warn("NotificationService fallback: sendPushNotification to: {}", request.getUserId());
        return ResponseEntity.ok(NotificationResponse.builder()
                .success(false)
                .message("Notification service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<NotificationPreferences> getNotificationPreferences(String userId) {
        log.warn("NotificationService fallback: getNotificationPreferences for userId: {}", userId);
        return ResponseEntity.ok(NotificationPreferences.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(true)
                .build());
    }

    @Override
    public ResponseEntity<Void> updateNotificationPreferences(String userId, NotificationPreferences preferences) {
        log.warn("NotificationService fallback: updateNotificationPreferences for userId: {}", userId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<List<NotificationHistory>> getNotificationHistory(String userId, int page, int size) {
        log.warn("NotificationService fallback: getNotificationHistory for userId: {}", userId);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Void> markAsRead(String notificationId) {
        log.warn("NotificationService fallback: markAsRead for notificationId: {}", notificationId);
        return ResponseEntity.ok().build();
    }
}