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
 * Balance Response DTO
 * 
 * Response structure for wallet balance queries.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {
    
    @JsonProperty("wallet_id")
    private UUID walletId;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("total_balance")
    private BigDecimal totalBalance;
    
    @JsonProperty("available")
    private BigDecimal available;
    
    @JsonProperty("held")
    private BigDecimal held;
    
    @JsonProperty("pending_credits")
    private BigDecimal pendingCredits;
    
    @JsonProperty("pending_debits")
    private BigDecimal pendingDebits;
    
    @JsonProperty("as_of")
    private Instant asOf;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
}