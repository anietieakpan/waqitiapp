package com.waqiti.merchant.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Method Response DTO
 * Response containing payment method details for merchants
 */
@Data
@Builder
public class PaymentMethodResponse {
    private UUID id;
    private UUID merchantId;
    private String paymentType;
    private String providerName;
    private String displayName;
    private boolean enabled;
    private boolean verified;
    private Map<String, String> configuration;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
    private Double processingFeePercentage;
    private String currency;
    private String accountIdentifier;
}