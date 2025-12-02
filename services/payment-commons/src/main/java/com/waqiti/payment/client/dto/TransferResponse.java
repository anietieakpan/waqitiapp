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
 * Transfer Response DTO
 * 
 * Response structure for wallet transfer operations.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    
    @JsonProperty("transfer_id")
    private String transferId;
    
    @JsonProperty("source_wallet_id")
    private UUID sourceWalletId;
    
    @JsonProperty("destination_wallet_id")
    private UUID destinationWalletId;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("status")
    private TransferStatus status;
    
    @JsonProperty("source_transaction_id")
    private String sourceTransactionId;
    
    @JsonProperty("destination_transaction_id")
    private String destinationTransactionId;
    
    @JsonProperty("reference")
    private String reference;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("completed_at")
    private Instant completedAt;
    
    @JsonProperty("fees")
    private BigDecimal fees;
    
    @JsonProperty("exchange_rate")
    private BigDecimal exchangeRate;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    public enum TransferStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, REVERSED, CANCELLED
    }
}