package com.waqiti.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet Response DTO
 * 
 * Unified response structure for wallet operations across all services.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    
    @JsonProperty("wallet_id")
    private UUID walletId;
    
    @JsonProperty("user_id")
    private UUID userId;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("balance")
    private BigDecimal balance;
    
    @JsonProperty("available_balance")
    private BigDecimal availableBalance;
    
    @JsonProperty("held_balance")
    private BigDecimal heldBalance;
    
    @JsonProperty("status")
    private WalletStatus status;
    
    @JsonProperty("type")
    private WalletType type;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("updated_at")
    private Instant updatedAt;
    
    @JsonProperty("locked")
    private Boolean locked;
    
    @JsonProperty("lock_reason")
    private String lockReason;
    
    @JsonProperty("daily_limit")
    private BigDecimal dailyLimit;
    
    @JsonProperty("monthly_limit")
    private BigDecimal monthlyLimit;
    
    @JsonProperty("daily_spent")
    private BigDecimal dailySpent;
    
    @JsonProperty("monthly_spent")
    private BigDecimal monthlySpent;
    
    // For error responses
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("error_code")
    private String errorCode;
    
    public enum WalletStatus {
        ACTIVE, INACTIVE, SUSPENDED, CLOSED, LOCKED, FROZEN
    }
    
    public enum WalletType {
        PERSONAL, BUSINESS, SAVINGS, INVESTMENT, ESCROW, MERCHANT
    }
}