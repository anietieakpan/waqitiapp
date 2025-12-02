package com.waqiti.merchant.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Method Request DTO
 * Request for adding or updating merchant payment methods
 */
@Data
public class PaymentMethodRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;
    
    @NotBlank(message = "Payment type is required")
    private String paymentType;
    
    @NotBlank(message = "Provider name is required")
    private String providerName;
    
    @NotBlank(message = "Display name is required")
    private String displayName;
    
    private boolean enabled = true;
    private Map<String, String> configuration;
    private Map<String, Object> metadata;
    private Double processingFeePercentage;
    private String currency = "USD";
    private String accountIdentifier;
}