package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Email Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    
    private String requestId;
    private String messageId;
    private String recipient;
    private String status;
    private String provider;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant openedAt;
    private Instant clickedAt;
    private Integer statusCode;
    private String errorMessage;
    private Map<String, Object> additionalData;
    
    // Delivery tracking
    private DeliveryInfo deliveryInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private String bounceType;
        private String bounceReason;
        private String complaintType;
        private String rejectionReason;
        private Integer attemptCount;
        private Instant lastAttemptAt;
        private Instant nextRetryAt;
    }
}