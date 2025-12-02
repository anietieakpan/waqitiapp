package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to debit an account")
public class AccountDebitRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Amount to debit", example = "100.50", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Schema(description = "Currency of the amount", example = "USD", required = true)
    private String currency;

    @NotBlank(message = "Transaction ID is required")
    @Schema(description = "Unique transaction identifier", example = "txn-12345678", required = true)
    private String transactionId;

    @NotBlank(message = "Idempotency key is required for duplicate prevention")
    @Size(min = 1, max = 100, message = "Idempotency key must be between 1 and 100 characters")
    @Schema(description = "Idempotency key to prevent duplicate processing. Must be unique per request. Use UUID recommended.",
            example = "idem-550e8400-e29b-41d4-a716-446655440000", required = true)
    private String idempotencyKey;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Schema(description = "Transaction description", example = "Payment to merchant", required = true)
    private String description;

    @Schema(description = "Reference ID for the transaction", example = "ref-987654")
    private String referenceId;

    @Schema(description = "Whether to allow overdraft", example = "false")
    private Boolean allowOverdraft = false;

    @Schema(description = "Reservation ID if using reserved funds", example = "res-12345678")
    private String reservationId;

    @Schema(description = "Additional transaction metadata")
    private Map<String, Object> metadata;
}