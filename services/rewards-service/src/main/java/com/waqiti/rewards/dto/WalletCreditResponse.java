package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for wallet credit response from Wallet Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreditResponse {
    
    private String transactionId;
    private String walletId;
    private BigDecimal amount;
    private String currency;
    private String status; // COMPLETED, PENDING, FAILED
    private String transactionType;
    private String referenceId;
    private String description;
    
    // Updated balances
    private BigDecimal newBalance;
    private BigDecimal previousBalance;
    private BigDecimal availableBalance;
    
    // Transaction details
    private LocalDateTime processedAt;
    private String processedBy;
    private String processingReference;
    
    // Response metadata
    private Map<String, Object> metadata;
    
    // Error information (if status is FAILED)
    private String errorCode;
    private String errorMessage;
}