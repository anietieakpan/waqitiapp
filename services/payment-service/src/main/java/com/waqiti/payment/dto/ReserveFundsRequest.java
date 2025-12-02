package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserve Funds Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveFundsRequest {
    private UUID walletId;
    private BigDecimal amount;
    private String reason;
    private Instant expiresAt;
}