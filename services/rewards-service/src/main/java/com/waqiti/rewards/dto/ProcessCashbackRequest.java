package com.waqiti.rewards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessCashbackRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    private String merchantId;
    
    private String merchantName;
    
    private String merchantCategory;
    
    @NotNull(message = "Transaction amount is required")
    @Positive(message = "Transaction amount must be positive")
    private BigDecimal transactionAmount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}