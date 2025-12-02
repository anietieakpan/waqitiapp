package com.waqiti.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Credit Request DTO
 * 
 * Request structure for crediting (adding) funds to a wallet.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum limit")
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @JsonProperty("currency")
    private String currency;
    
    @NotBlank(message = "Reference is required")
    @Size(max = 100, message = "Reference must not exceed 100 characters")
    @JsonProperty("reference")
    private String reference;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @JsonProperty("description")
    private String description;
    
    @NotBlank(message = "Idempotency key is required")
    @Size(max = 64, message = "Idempotency key must not exceed 64 characters")
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("payment_id")
    private String paymentId;
    
    @JsonProperty("apply_limits")
    @Builder.Default
    private Boolean applyLimits = true;
}