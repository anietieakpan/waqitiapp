package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Refund Request DTO
 * 
 * Contains information needed to process a refund
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    
    @NotBlank(message = "Request ID is required")
    private String requestId;
    
    @NotBlank(message = "Original transaction ID is required")
    private String originalTransactionId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
    
    @NotBlank(message = "Reason is required")
    private String reason;
    
    @Size(max = 1000, message = "Reason details cannot exceed 1000 characters")
    private String reasonDetails;
    
    // Optional fields
    private String merchantId;
    private String userId;
    
    // Metadata for tracking
    private Map<String, String> metadata;
    
    // Refund type
    private RefundType refundType = RefundType.FULL;
    
    public enum RefundType {
        FULL,
        PARTIAL
    }
}