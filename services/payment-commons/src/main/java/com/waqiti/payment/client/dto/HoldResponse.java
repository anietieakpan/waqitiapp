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
 * Hold Response DTO
 * 
 * Response structure for hold operations on wallet funds.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldResponse {
    
    @JsonProperty("hold_id")
    private String holdId;
    
    @JsonProperty("wallet_id")
    private UUID walletId;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("reference")
    private String reference;
    
    @JsonProperty("status")
    private HoldStatus status;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("expires_at")
    private Instant expiresAt;
    
    @JsonProperty("released_at")
    private Instant releasedAt;
    
    @JsonProperty("captured_at")
    private Instant capturedAt;
    
    public enum HoldStatus {
        ACTIVE, RELEASED, CAPTURED, EXPIRED, CANCELLED
    }
}