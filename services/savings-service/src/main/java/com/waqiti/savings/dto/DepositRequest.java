package com.waqiti.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for depositing money into a savings account.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to deposit money")
public class DepositRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Deposit amount exceeds maximum limit")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Deposit amount", example = "500.00", required = true)
    private BigDecimal amount;

    @Schema(description = "Payment method", example = "BANK_ACCOUNT")
    private String paymentMethod;

    @Schema(description = "Funding source ID (e.g., bank account, card)")
    private UUID fundingSourceId;

    @Schema(description = "Transaction reference/ID from external system")
    @Size(max = 100, message = "Transaction ID too long")
    private String transactionId;

    @Schema(description = "Source of deposit", example = "SALARY")
    private String source;

    @Schema(description = "Optional note for the deposit")
    @Size(max = 500, message = "Note too long")
    private String note;

    @Schema(description = "Idempotency key to prevent duplicate deposits")
    @Size(max = 100, message = "Idempotency key too long")
    private String idempotencyKey;
}
