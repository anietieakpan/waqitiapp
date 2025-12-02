package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Payment request DTO for payment service integration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private UUID userId;
    private UUID recipientId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String paymentMethodId;
    private String description;
    private Map<String, Object> metadata;
    private String idempotencyKey;

    // AR-specific fields
    private String arExperienceId;
    private String arSessionToken;
    private String gestureType;
    private Float gestureConfidence;
}
