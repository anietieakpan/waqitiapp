package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Money transfer response")
public class TransferResponseDto {

    @Schema(description = "Transaction identifier", example = "txn-12345678")
    private String transactionId;

    @Schema(description = "Transfer status", 
            example = "COMPLETED",
            allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"})
    private String status;

    @Schema(description = "Source account identifier", example = "acc-from-123")
    private String fromAccountId;

    @Schema(description = "Destination account identifier", example = "acc-to-456")
    private String toAccountId;

    @Schema(description = "Transfer amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Transfer description", example = "Payment to friend")
    private String description;

    @Schema(description = "Transfer reference", example = "ref-987654")
    private String reference;

    @Schema(description = "Processing timestamp", example = "2024-01-15T15:30:00Z")
    private Instant processedAt;

    @Schema(description = "Error message if transfer failed", example = "Insufficient funds")
    private String errorMessage;
}