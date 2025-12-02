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
public class NotificationResult {

    private UUID notificationId;
    
    private boolean successful;
    
    private String message;
    
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
    
    private List<NotificationDelivery> deliveries;
    
    private Map<String, Object> metadata;
    
    private String failureReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationDelivery {
        private String channel;
        private String recipient;
        private boolean delivered;
        private LocalDateTime deliveredAt;
        private String deliveryStatus;
        private String errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean hasFailures() {
        return deliveries != null && 
               deliveries.stream().anyMatch(d -> !d.isDelivered());
    }

    public int getDeliveryCount() {
        return deliveries != null ? deliveries.size() : 0;
    }

    public int getSuccessfulDeliveries() {
        return deliveries != null ? 
               (int) deliveries.stream().filter(NotificationDelivery::isDelivered).count() : 0;
    }
}