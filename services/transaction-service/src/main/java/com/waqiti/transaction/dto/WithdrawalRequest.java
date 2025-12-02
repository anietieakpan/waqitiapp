package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {
    
    @NotNull(message = "Wallet ID is required")
    private String walletId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    private Map<String, String> metadata;
    
    private String targetAccountId;
    
    private String paymentMethodId;
    
    private String paymentProvider;
    
    private UUID requesterId;
    
    private String reference;
}