package com.waqiti.rewards.dto;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request DTO for cashback estimation
 */
@Data
@Builder
public class CashbackEstimateRequest {
    
    /**
     * Transaction amount
     */
    @NotNull
    @Positive
    private BigDecimal amount;
    
    /**
     * Merchant ID (optional)
     */
    private String merchantId;
    
    /**
     * Merchant category code
     */
    private String merchantCategory;
    
    /**
     * Transaction currency
     */
    @NotBlank
    private String currency;
    
    /**
     * Payment method type (optional)
     */
    private String paymentMethod;
    
    /**
     * Transaction type (optional)
     */
    private String transactionType;
    
    /**
     * Additional metadata for calculation
     */
    private java.util.Map<String, Object> metadata;
}