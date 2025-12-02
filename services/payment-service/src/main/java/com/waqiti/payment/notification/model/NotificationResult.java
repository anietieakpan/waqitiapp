package com.waqiti.payment.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Notification Result
 * 
 * Comprehensive result for notification delivery operations including:
 * - Delivery status and detailed tracking information
 * - Multi-channel delivery results (email, SMS, push, webhook)
 * - Retry tracking and delivery attempts
 * - Performance metrics and timing data
 * - Error handling and troubleshooting information
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResult {
    
    // Basic notification result
    private String notificationId;
    private NotificationType notificationType;
    private DeliveryStatus deliveryStatus;
    private String errorMessage;
    private String errorCode;
    private List<DeliveryError> deliveryErrors;
    
    // Delivery tracking
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant acknowledgedAt;
    private int deliveryAttempts;
    private int maxRetryAttempts;
    private Instant nextRetryAt;
    
    // Multi-channel results
    private Map<NotificationChannel, ChannelDeliveryResult> channelResults;
    private int successfulChannels;
    private int failedChannels;
    private int totalChannels;
    
    // Recipient information
    private String recipientId;
    private String recipientEmail;
    private String recipientPhone;
    private List<String> webhookUrls;
    
    // Performance metrics
    private long deliveryTimeMs;
    private long processingTimeMs;
    private long queueWaitTimeMs;
    private int priority;
    
    // Content and metadata
    private String subject;
    private String messageTemplate;
    private Map<String, Object> templateVariables;
    private Map<String, Object> metadata;
    
    // Enums
    public enum NotificationType {
        REFUND_NOTIFICATION,
        RECONCILIATION_NOTIFICATION,
        CUSTOMER_ACTIVATION,
        PAYMENT_COMPLETION,
        PAYMENT_FAILURE,
        SECURITY_ALERT,
        OPERATIONAL_ALERT,
        SYSTEM_MAINTENANCE,
        CUSTOM
    }
    
    public enum DeliveryStatus {
        PENDING,
        QUEUED,
        SENDING,
        DELIVERED,
        FAILED,
        RETRY_SCHEDULED,
        CANCELLED,
        ACKNOWLEDGED,
        EXPIRED
    }
    
    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH_NOTIFICATION,
        WEBHOOK,
        SLACK,
        MICROSOFT_TEAMS,
        DISCORD,
        IN_APP,
        KAFKA_EVENT
    }
    
    // Helper methods
    public boolean isSuccessful() {
        return deliveryStatus == DeliveryStatus.DELIVERED || 
               deliveryStatus == DeliveryStatus.ACKNOWLEDGED;
    }
    
    public boolean isFailed() {
        return deliveryStatus == DeliveryStatus.FAILED || 
               deliveryStatus == DeliveryStatus.EXPIRED;
    }
    
    public boolean isPending() {
        return deliveryStatus == DeliveryStatus.PENDING ||
               deliveryStatus == DeliveryStatus.QUEUED ||
               deliveryStatus == DeliveryStatus.SENDING;
    }
    
    public boolean canRetry() {
        return deliveryAttempts < maxRetryAttempts && 
               (deliveryStatus == DeliveryStatus.FAILED || 
                deliveryStatus == DeliveryStatus.RETRY_SCHEDULED);
    }
    
    public double getSuccessRate() {
        if (totalChannels == 0) return 0.0;
        return (double) successfulChannels / totalChannels * 100.0;
    }
    
    // Static factory methods
    public static NotificationResult success(String notificationId, NotificationType type) {
        return NotificationResult.builder()
            .notificationId(notificationId)
            .notificationType(type)
            .deliveryStatus(DeliveryStatus.DELIVERED)
            .deliveryAttempts(1)
            .sentAt(Instant.now())
            .deliveredAt(Instant.now())
            .build();
    }
    
    public static NotificationResult failed(String notificationId, NotificationType type, String errorMessage) {
        return NotificationResult.builder()
            .notificationId(notificationId)
            .notificationType(type)
            .deliveryStatus(DeliveryStatus.FAILED)
            .errorMessage(errorMessage)
            .deliveryAttempts(1)
            .sentAt(Instant.now())
            .build();
    }
    
    public static NotificationResult pending(String notificationId, NotificationType type) {
        return NotificationResult.builder()
            .notificationId(notificationId)
            .notificationType(type)
            .deliveryStatus(DeliveryStatus.PENDING)
            .deliveryAttempts(0)
            .maxRetryAttempts(3)
            .build();
    }
    
    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryError {
        private NotificationChannel channel;
        private String errorCode;
        private String errorMessage;
        private Instant occurredAt;
        private boolean isRetryable;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelDeliveryResult {
        private NotificationChannel channel;
        private DeliveryStatus status;
        private String externalId;
        private String providerResponse;
        private Instant sentAt;
        private Instant deliveredAt;
        private long deliveryTimeMs;
        private String errorMessage;
        private int attempts;
    }
}