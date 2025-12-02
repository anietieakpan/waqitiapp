package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tracks delivery status and metrics for notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTracker {
    
    private String trackingId;
    private String notificationId;
    private String recipientId;
    private String channel; // email, sms, push
    
    // Delivery status
    private String status; // pending, sent, delivered, failed, bounced
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime failedAt;
    
    // Provider information
    private String provider;
    private String providerMessageId;
    private String providerStatus;
    private Map<String, Object> providerResponse;
    
    // Delivery attempts
    private int attemptCount;
    private List<DeliveryAttempt> attempts;
    
    // Error information
    private String errorCode;
    private String errorMessage;
    private String errorCategory; // temporary, permanent, unknown
    
    // Metrics
    private long deliveryTimeMs;
    private double deliveryScore;
    
    // Metadata
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryAttempt {
        private int attemptNumber;
        private LocalDateTime attemptedAt;
        private String status;
        private String provider;
        private String errorMessage;
        private Map<String, Object> response;
    }
}