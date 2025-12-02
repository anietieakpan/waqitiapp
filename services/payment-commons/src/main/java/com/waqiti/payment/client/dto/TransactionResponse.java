package com.waqiti.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Transaction Response DTO
 * 
 * Unified response for wallet transaction operations.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @JsonProperty("wallet_id")
    private UUID walletId;
    
    @JsonProperty("type")
    private TransactionType type;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("balance_before")
    private BigDecimal balanceBefore;
    
    @JsonProperty("balance_after")
    private BigDecimal balanceAfter;
    
    @JsonProperty("status")
    private TransactionStatus status;
    
    @JsonProperty("reference")
    private String reference;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("completed_at")
    private Instant completedAt;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    @JsonProperty("reversal_of")
    private String reversalOf;
    
    @JsonProperty("reversed_by")
    private String reversedBy;
    
    public enum TransactionType {
        CREDIT, DEBIT, TRANSFER_IN, TRANSFER_OUT, HOLD, RELEASE, REVERSAL, FEE, ADJUSTMENT
    }
    
    public enum TransactionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, REVERSED, CANCELLED
    }
}