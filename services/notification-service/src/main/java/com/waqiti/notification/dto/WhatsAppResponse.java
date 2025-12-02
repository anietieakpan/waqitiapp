package com.waqiti.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WhatsApp API response DTO
 */
@Data
@Builder
public class WhatsAppResponse {
    
    private boolean success;
    private String messageId;
    private String correlationId;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;
    private DeliveryStatus deliveryStatus;
    private Map<String, Object> metadata;
    
    public enum DeliveryStatus {
        SENT,
        DELIVERED,
        READ,
        FAILED,
        PENDING
    }
}