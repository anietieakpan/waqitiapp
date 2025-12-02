package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID notificationId;
    
    private NotificationStatus status;
    
    private String message;
    
    private String errorCode;
    
    private String errorMessage;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    private String externalId;
    
    private String provider;
    
    private int deliveryAttempts;
    
    private LocalDateTime deliveredAt;
    
    private LocalDateTime readAt;
    
    private String recipientId;
    
    private NotificationChannel channel;
    
    private Map<String, Object> metadata;
    
    private List<DeliveryEvent> deliveryEvents;

    public enum NotificationStatus {
        QUEUED,
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED,
        REJECTED,
        EXPIRED
    }

    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        SLACK,
        WEBHOOK
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryEvent {
        private String event;
        private LocalDateTime timestamp;
        private String description;
        private Map<String, Object> eventData;
    }

    public boolean isSuccessful() {
        return NotificationStatus.SENT.equals(status) || 
               NotificationStatus.DELIVERED.equals(status) ||
               NotificationStatus.READ.equals(status);
    }

    public boolean isFailed() {
        return NotificationStatus.FAILED.equals(status) ||
               NotificationStatus.REJECTED.equals(status) ||
               NotificationStatus.EXPIRED.equals(status);
    }

    public boolean isPending() {
        return NotificationStatus.QUEUED.equals(status) ||
               NotificationStatus.SENDING.equals(status);
    }

    public boolean isDelivered() {
        return deliveredAt != null;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean hasError() {
        return errorCode != null || errorMessage != null;
    }

    public long getDeliveryTimeMillis() {
        if (timestamp != null && deliveredAt != null) {
            return java.time.Duration.between(timestamp, deliveredAt).toMillis();
        }
        return -1;
    }
}