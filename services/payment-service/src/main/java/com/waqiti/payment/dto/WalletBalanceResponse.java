package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wallet Balance Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletBalanceResponse {
    private UUID walletId;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalBalance;
}