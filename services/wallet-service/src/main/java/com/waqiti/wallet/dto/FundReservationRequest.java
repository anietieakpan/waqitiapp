package com.waqiti.wallet.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fund Reservation Request DTO
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundReservationRequest {

    @NotNull(message = "Wallet ID is required")
    private UUID walletId;

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 digits and 4 decimal places")
    private BigDecimal amount;

    /**
     * Time-to-live in minutes. Default: 5 minutes
     * After TTL expires, reservation is auto-released by cleanup job
     */
    @Min(value = 1, message = "TTL must be at least 1 minute")
    @Max(value = 60, message = "TTL cannot exceed 60 minutes")
    private Integer ttlMinutes;

    /**
     * Reason for reservation (for audit logging)
     */
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    /**
     * Idempotency key to prevent duplicate reservations
     * If provided, system will reject duplicate requests with same key
     */
    @Size(max = 255, message = "Idempotency key cannot exceed 255 characters")
    private String idempotencyKey;
}
