package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction details response")
public class TransactionResponseDto {

    @Schema(description = "Transaction identifier", example = "txn-12345678")
    private String transactionId;

    @Schema(description = "Original transaction ID for reversals", example = "txn-original-123")
    private String originalTransactionId;

    @Schema(description = "Transaction status", 
            example = "COMPLETED",
            allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED", "REVERSED"})
    private String status;

    @Schema(description = "Transaction type", 
            example = "TRANSFER",
            allowableValues = {"TRANSFER", "PAYMENT", "DEPOSIT", "WITHDRAWAL", "REVERSAL"})
    private String type;

    @Schema(description = "Transaction amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Transaction description", example = "Payment to merchant")
    private String description;

    @Schema(description = "Source account identifier", example = "acc-from-123")
    private String fromAccountId;

    @Schema(description = "Destination account identifier", example = "acc-to-456")
    private String toAccountId;

    @Schema(description = "Transaction creation timestamp", example = "2024-01-15T15:30:00Z")
    private Instant createdAt;

    @Schema(description = "Transaction processing timestamp", example = "2024-01-15T15:30:30Z")
    private Instant processedAt;

    @Schema(description = "Additional transaction metadata")
    private Map<String, Object> metadata;
}