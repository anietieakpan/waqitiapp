package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPointsRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @NotBlank(message = "Transaction type is required")
    private String transactionType;
    
    private String merchantCategory;
    
    private String merchantId;
    
    private String recipientId; // For P2P transfers
    
    private String campaignId; // For campaign-specific points
    
    private Integer bonusMultiplier; // For special promotions
    
    private LocalDateTime transactionDate;
    
    private String source; // Source system or channel
    
    private String referenceId; // External reference
    
    private String notes;
}