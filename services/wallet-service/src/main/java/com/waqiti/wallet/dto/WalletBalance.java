package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Wallet balance information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalance {
    private String walletId;
    private String userId;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private String currency;
    private LocalDateTime lastUpdated;
    private Map<String, BigDecimal> limits;
    private boolean active;
    private String status;
}