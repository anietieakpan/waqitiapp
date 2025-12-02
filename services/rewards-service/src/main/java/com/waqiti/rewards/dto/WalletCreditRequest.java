package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for wallet credit request to Wallet Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreditRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    @NotBlank(message = "Transaction type is required")
    private String transactionType; // REWARDS_CASHBACK, REWARDS_POINTS_REDEMPTION, REWARDS_BONUS
    
    @NotBlank(message = "Reference ID is required")
    private String referenceId;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotBlank(message = "Source is required")
    private String source; // REWARDS_SERVICE
    
    // For rewards tracking
    private String rewardType; // CASHBACK, POINTS_CONVERSION, REFERRAL_BONUS
    private String originalTransactionId;
    private BigDecimal originalTransactionAmount;
    private String merchantName;
    private String merchantCategory;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    // Idempotency key to prevent duplicate credits
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}