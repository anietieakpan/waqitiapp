package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SMS delivery status tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsDeliveryStatus {
    
    private String messageId;
    private String phoneNumber;
    private DeliveryState state;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant readAt;
    private String providerStatus;
    private String providerMessageId;
    private int attemptCount;
    private String failureReason;
    private String carrier;
    private String country;
    private double deliveryScore;
    
    /**
     * Check if message was successfully delivered
     */
    public boolean isDelivered() {
        return state == DeliveryState.DELIVERED || state == DeliveryState.READ;
    }
    
    /**
     * Check if message failed permanently
     */
    public boolean isFailed() {
        return state == DeliveryState.FAILED || state == DeliveryState.REJECTED;
    }
    
    /**
     * Check if message is still being processed
     */
    public boolean isPending() {
        return state == DeliveryState.PENDING || state == DeliveryState.SENT;
    }
    
    /**
     * Get delivery latency in milliseconds
     */
    public long getDeliveryLatencyMs() {
        if (sentAt != null && deliveredAt != null) {
            return deliveredAt.toEpochMilli() - sentAt.toEpochMilli();
        }
        return -1;
    }
}