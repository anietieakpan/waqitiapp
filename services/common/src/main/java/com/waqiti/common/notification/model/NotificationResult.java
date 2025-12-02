package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Result of a notification send operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResult {
    
    /**
     * Unique notification ID
     */
    private String notificationId;
    
    /**
     * Request ID that initiated this notification
     */
    private String requestId;
    
    /**
     * Delivery status
     */
    private DeliveryStatus status;
    
    /**
     * Provider-specific message ID
     */
    private String providerMessageId;
    
    /**
     * Channel used for delivery
     */
    private NotificationChannel channel;
    
    /**
     * Timestamp when notification was sent
     */
    private Instant sentAt;
    
    /**
     * Timestamp when notification was delivered
     */
    private Instant deliveredAt;
    
    /**
     * Timestamp when notification was read/opened
     */
    private Instant readAt;
    
    /**
     * Error details if failed
     */
    private ErrorDetails errorDetails;
    
    /**
     * Delivery attempts made
     */
    private int attemptCount;
    
    /**
     * Provider used for sending
     */
    private String provider;
    
    /**
     * Cost of sending this notification
     */
    private NotificationCost cost;
    
    /**
     * Recipients who received this notification
     */
    private java.util.List<String> recipients;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Delivery receipt from provider
     */
    private DeliveryReceipt deliveryReceipt;
    
    /**
     * Whether notification was queued for retry
     */
    private boolean queuedForRetry;

    /**
     * Next retry time if queued
     */
    private Instant nextRetryAt;

    /**
     * Check if notification was successful
     */
    public boolean isSuccess() {
        return status == DeliveryStatus.SENT ||
               status == DeliveryStatus.DELIVERED ||
               status == DeliveryStatus.QUEUED;
    }

    /**
     * Get error message if failed
     */
    public String getErrorMessage() {
        if (errorDetails != null) {
            return errorDetails.getMessage();
        }
        return null;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails {
        private String code;
        private String message;
        private String providerError;
        private Map<String, Object> context;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationCost {
        private double amount;
        private String currency;
        private String unit; // per message, per segment, etc.
        private int segments; // for SMS
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryReceipt {
        private String receiptId;
        private Instant timestamp;
        private String status;
        private Map<String, String> providerData;
    }
    
    public enum DeliveryStatus {
        PENDING,
        QUEUED,
        SENDING,
        SENT,
        DELIVERED,
        FAILED,
        BOUNCED,
        REJECTED,
        EXPIRED,
        CLICKED,
        OPENED,
        UNSUBSCRIBED,
        COMPLAINED
    }
}