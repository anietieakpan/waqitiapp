package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reserve funds from an account")
public class FundReservationRequestDto {

    @NotBlank(message = "Reservation ID is required")
    @Schema(description = "Unique identifier for the reservation", example = "res-12345678", required = true)
    private String reservationId;

    @NotBlank(message = "Idempotency key is required for duplicate prevention")
    @Size(min = 1, max = 100, message = "Idempotency key must be between 1 and 100 characters")
    @Schema(description = "Idempotency key to prevent duplicate processing. Must be unique per request. Use UUID recommended.",
            example = "idem-550e8400-e29b-41d4-a716-446655440000", required = true)
    private String idempotencyKey;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Amount to reserve", example = "100.50", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Schema(description = "Currency of the amount", example = "USD", required = true)
    private String currency;

    @NotBlank(message = "Purpose is required")
    @Size(max = 500, message = "Purpose cannot exceed 500 characters")
    @Schema(description = "Purpose of the reservation", example = "Payment processing hold", required = true)
    private String purpose;

    @Schema(description = "Related transaction ID", example = "txn-987654")
    private String transactionId;

    @Schema(description = "Expiration time for the reservation", example = "2024-01-15T16:00:00Z")
    private Instant expiresAt;

    @Schema(description = "Whether to allow overdraft for reservation", example = "false")
    private Boolean allowOverdraft = false;

    @Schema(description = "Priority level of the reservation", 
            example = "NORMAL",
            allowableValues = {"LOW", "NORMAL", "HIGH", "CRITICAL"})
    private String priority = "NORMAL";
}