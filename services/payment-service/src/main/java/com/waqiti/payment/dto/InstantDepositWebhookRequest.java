package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook request for instant deposit status updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantDepositWebhookRequest {
    private String eventType;
    private String eventId;
    private String networkTransactionId;
    private String internalTransactionId;
    private String status;
    private String failureReason;
    private String failureCode;
    private LocalDateTime eventTimestamp;
    private Map<String, Object> metadata;
    
    // Webhook verification
    private String signature;
    private String timestamp;
}