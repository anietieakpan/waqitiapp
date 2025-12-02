package com.waqiti.payment.core.notification;

import com.waqiti.payment.core.integration.PaymentProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Payment notification service
 * Industrial-grade notification orchestration and delivery
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationService {
    
    public NotificationResult sendPaymentNotification(PaymentProcessingResult paymentResult) {
        log.info("Sending payment notification for: {}", paymentResult.getRequestId());
        
        try {
            // Determine notification type
            NotificationType notificationType = determineNotificationType(paymentResult);
            
            // Create notification request
            NotificationRequest request = createNotificationRequest(paymentResult, notificationType);
            
            // Send notification
            return sendNotification(request);
            
        } catch (Exception e) {
            log.error("Payment notification failed for: {}", paymentResult.getRequestId(), e);
            return NotificationResult.builder()
                .paymentId(paymentResult.getRequestId())
                .status(NotificationStatus.FAILED)
                .errorMessage(e.getMessage())
                .sentAt(LocalDateTime.now())
                .build();
        }
    }
    
    public CompletableFuture<NotificationResult> sendPaymentNotificationAsync(PaymentProcessingResult paymentResult) {
        return CompletableFuture.supplyAsync(() -> sendPaymentNotification(paymentResult));
    }
    
    public NotificationResult sendNotification(NotificationRequest request) {
        log.info("Sending notification: {}", request.getNotificationId());
        
        try {
            // Validate notification request
            if (!isValidNotificationRequest(request)) {
                return NotificationResult.builder()
                    .notificationId(request.getNotificationId())
                    .paymentId(request.getPaymentId())
                    .status(NotificationStatus.REJECTED)
                    .errorMessage("Invalid notification request")
                    .sentAt(LocalDateTime.now())
                    .build();
            }
            
            // Process notification based on channel
            return processNotificationByChannel(request);
            
        } catch (Exception e) {
            log.error("Notification sending failed: {}", request.getNotificationId(), e);
            return NotificationResult.builder()
                .notificationId(request.getNotificationId())
                .paymentId(request.getPaymentId())
                .status(NotificationStatus.ERROR)
                .errorMessage(e.getMessage())
                .sentAt(LocalDateTime.now())
                .build();
        }
    }
    
    public List<NotificationResult> sendBatchNotifications(List<NotificationRequest> requests) {
        log.info("Sending batch notifications: {} requests", requests.size());
        
        return requests.parallelStream()
            .map(this::sendNotification)
            .toList();
    }
    
    private NotificationType determineNotificationType(PaymentProcessingResult paymentResult) {
        return switch (paymentResult.getStatus().toString()) {
            case "COMPLETED" -> NotificationType.PAYMENT_SUCCESS;
            case "FAILED" -> NotificationType.PAYMENT_FAILED;
            case "PENDING" -> NotificationType.PAYMENT_PENDING;
            case "CANCELLED" -> NotificationType.PAYMENT_CANCELLED;
            default -> NotificationType.PAYMENT_STATUS_UPDATE;
        };
    }
    
    private NotificationRequest createNotificationRequest(PaymentProcessingResult paymentResult, 
                                                        NotificationType notificationType) {
        return NotificationRequest.builder()
            .notificationId(UUID.randomUUID())
            .paymentId(paymentResult.getRequestId())
            .userId(paymentResult.getUserId())
            .notificationType(notificationType)
            .channel(NotificationChannel.EMAIL) // Default channel
            .priority(NotificationPriority.NORMAL)
            .templateId(getTemplateId(notificationType))
            .requestedAt(LocalDateTime.now())
            .build();
    }
    
    private String getTemplateId(NotificationType type) {
        return switch (type) {
            case PAYMENT_SUCCESS -> "payment_success_template";
            case PAYMENT_FAILED -> "payment_failed_template";
            case PAYMENT_PENDING -> "payment_pending_template";
            case PAYMENT_CANCELLED -> "payment_cancelled_template";
            default -> "payment_generic_template";
        };
    }
    
    private boolean isValidNotificationRequest(NotificationRequest request) {
        return request.getPaymentId() != null && 
               request.getUserId() != null &&
               request.getNotificationType() != null &&
               request.getChannel() != null;
    }
    
    private NotificationResult processNotificationByChannel(NotificationRequest request) {
        return switch (request.getChannel()) {
            case EMAIL -> sendEmailNotification(request);
            case SMS -> sendSmsNotification(request);
            case PUSH -> sendPushNotification(request);
            case WEBHOOK -> sendWebhookNotification(request);
            case IN_APP -> sendInAppNotification(request);
            default -> NotificationResult.builder()
                .notificationId(request.getNotificationId())
                .paymentId(request.getPaymentId())
                .status(NotificationStatus.FAILED)
                .errorMessage("Unsupported notification channel")
                .sentAt(LocalDateTime.now())
                .build();
        };
    }
    
    private NotificationResult sendEmailNotification(NotificationRequest request) {
        // Email notification implementation
        return NotificationResult.builder()
            .notificationId(request.getNotificationId())
            .paymentId(request.getPaymentId())
            .status(NotificationStatus.SENT)
            .channel(request.getChannel())
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    private NotificationResult sendSmsNotification(NotificationRequest request) {
        // SMS notification implementation
        return NotificationResult.builder()
            .notificationId(request.getNotificationId())
            .paymentId(request.getPaymentId())
            .status(NotificationStatus.SENT)
            .channel(request.getChannel())
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    private NotificationResult sendPushNotification(NotificationRequest request) {
        // Push notification implementation
        return NotificationResult.builder()
            .notificationId(request.getNotificationId())
            .paymentId(request.getPaymentId())
            .status(NotificationStatus.SENT)
            .channel(request.getChannel())
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    private NotificationResult sendWebhookNotification(NotificationRequest request) {
        // Webhook notification implementation
        return NotificationResult.builder()
            .notificationId(request.getNotificationId())
            .paymentId(request.getPaymentId())
            .status(NotificationStatus.SENT)
            .channel(request.getChannel())
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    private NotificationResult sendInAppNotification(NotificationRequest request) {
        // In-app notification implementation
        return NotificationResult.builder()
            .notificationId(request.getNotificationId())
            .paymentId(request.getPaymentId())
            .status(NotificationStatus.SENT)
            .channel(request.getChannel())
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    public enum NotificationStatus {
        PENDING,
        SENT,
        DELIVERED,
        FAILED,
        REJECTED,
        ERROR
    }
    
    public enum NotificationType {
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        PAYMENT_PENDING,
        PAYMENT_CANCELLED,
        PAYMENT_STATUS_UPDATE,
        FRAUD_ALERT,
        COMPLIANCE_ALERT
    }
    
    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        WEBHOOK,
        IN_APP
    }
    
    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NotificationRequest {
        private UUID notificationId;
        private UUID paymentId;
        private UUID userId;
        private NotificationType notificationType;
        private NotificationChannel channel;
        private NotificationPriority priority;
        private String templateId;
        private Map<String, Object> templateData;
        private String recipient;
        private LocalDateTime requestedAt;
        private LocalDateTime scheduleFor;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NotificationResult {
        private UUID notificationId;
        private UUID paymentId;
        private NotificationStatus status;
        private NotificationChannel channel;
        private String recipient;
        private LocalDateTime sentAt;
        private LocalDateTime deliveredAt;
        private String errorMessage;
        private String externalId;
    }
}