package com.waqiti.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Hold Request DTO
 * 
 * Request structure for placing holds on wallet funds.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
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
    
    @JsonProperty("expires_at")
    private Instant expiresAt;
    
    @JsonProperty("auto_release")
    @Builder.Default
    private Boolean autoRelease = true;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}