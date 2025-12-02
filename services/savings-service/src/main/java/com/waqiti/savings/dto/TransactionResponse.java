package com.waqiti.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for transaction operations (deposit, withdrawal, transfer).
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction result")
public class TransactionResponse {

    @Schema(description = "Transaction ID")
    private UUID transactionId;

    @Schema(description = "Account ID")
    private UUID accountId;

    @Schema(description = "Transaction type", example = "DEPOSIT")
    private String transactionType;

    @Schema(description = "Transaction amount", example = "500.00")
    private BigDecimal amount;

    @Schema(description = "Account balance after transaction", example = "2500.00")
    private BigDecimal newBalance;

    @Schema(description = "Available balance after transaction", example = "2500.00")
    private BigDecimal availableBalance;

    @Schema(description = "Transaction status", example = "COMPLETED")
    private String status;

    @Schema(description = "Transaction reference")
    private String transactionReference;

    @Schema(description = "Transaction description")
    private String description;

    @Schema(description = "Transaction timestamp")
    private LocalDateTime transactionDate;

    @Schema(description = "Success message")
    private String message;
}
