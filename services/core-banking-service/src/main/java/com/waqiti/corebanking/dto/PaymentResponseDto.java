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
@Schema(description = "Payment processing response")
public class PaymentResponseDto {

    @Schema(description = "Transaction identifier", example = "txn-12345678")
    private String transactionId;

    @Schema(description = "Payment status", 
            example = "COMPLETED",
            allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"})
    private String status;

    @Schema(description = "Sender user identifier", example = "user-123")
    private String fromUserId;

    @Schema(description = "Recipient user identifier", example = "user-456")
    private String toUserId;

    @Schema(description = "Payment amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Payment description", example = "Payment for services")
    private String description;

    @Schema(description = "Payment category", example = "P2P")
    private String category;

    @Schema(description = "Processing timestamp", example = "2024-01-15T15:30:00Z")
    private Instant processedAt;

    @Schema(description = "Error message if payment failed", example = "Insufficient funds")
    private String errorMessage;
}