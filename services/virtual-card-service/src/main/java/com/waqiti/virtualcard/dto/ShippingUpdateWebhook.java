package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.enums.ShippingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for shipping update webhook payloads
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingUpdateWebhook {
    
    private String trackingNumber;
    private ShippingStatus status;
    private String currentLocation;
    private Instant deliveredAt;
    
    private String carrier;
    private String statusDescription;
    private String eventCode;
    private Instant timestamp;
    
    private String signedBy;
    private Integer deliveryAttemptCount;
    private String exceptionCode;
    private String exceptionDescription;
    
    // Webhook metadata
    private String webhookId;
    private String eventType;
    private String source;
}