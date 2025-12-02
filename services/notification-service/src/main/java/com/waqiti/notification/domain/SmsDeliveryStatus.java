package com.waqiti.notification.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class SmsDeliveryStatus {
    private final String messageId;
    private final String recipientNumber;
    private final DeliveryState state;
    private final String statusMessage;
    private final LocalDateTime sentAt;
    private final LocalDateTime deliveredAt;
    private final LocalDateTime failedAt;
    private final Integer attemptCount;
    private final String errorCode;
    private final String errorMessage;
    private final String carrier;
    private final String countryCode;
    private final BigDecimal cost;
    private final Map<String, Object> metadata;
    
    public enum DeliveryState {
        QUEUED,
        SENDING,
        SENT,
        DELIVERED,
        FAILED,
        UNDELIVERED,
        BOUNCED,
        EXPIRED
    }
    
    public boolean isSuccessful() {
        return state == DeliveryState.DELIVERED;
    }
    
    public boolean isFailed() {
        return state == DeliveryState.FAILED || 
               state == DeliveryState.UNDELIVERED || 
               state == DeliveryState.BOUNCED ||
               state == DeliveryState.EXPIRED;
    }
    
    public boolean isPending() {
        return state == DeliveryState.QUEUED || 
               state == DeliveryState.SENDING || 
               state == DeliveryState.SENT;
    }
    
    public Long getDeliveryTimeMs() {
        if (sentAt != null && deliveredAt != null) {
            return java.time.Duration.between(sentAt, deliveredAt).toMillis();
        }
        return null;
    }
}