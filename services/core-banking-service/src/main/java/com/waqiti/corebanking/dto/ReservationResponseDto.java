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
@Schema(description = "Fund reservation response")
public class ReservationResponseDto {

    @Schema(description = "Reservation identifier", example = "res-12345678")
    private String reservationId;

    @Schema(description = "Account identifier", example = "acc-12345678")
    private String accountId;

    @Schema(description = "Reserved amount", example = "100.50")
    private BigDecimal amount;

    @Schema(description = "Currency of reserved amount", example = "USD")
    private String currency;

    @Schema(description = "Status of the reservation", 
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "RELEASED", "EXPIRED", "CONSUMED"})
    private String status;

    @Schema(description = "Purpose of the reservation", example = "Payment processing hold")
    private String purpose;

    @Schema(description = "Related transaction ID", example = "txn-987654")
    private String transactionId;

    @Schema(description = "Timestamp when reservation was created", example = "2024-01-15T15:30:00Z")
    private Instant createdAt;

    @Schema(description = "Expiration time for the reservation", example = "2024-01-15T16:00:00Z")
    private Instant expiresAt;

    @Schema(description = "Available balance after reservation", example = "1050.25")
    private BigDecimal availableBalanceAfter;

    @Schema(description = "Total reserved balance in account", example = "150.50")
    private BigDecimal totalReservedBalance;
}