package com.waqiti.notification.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceResponse {
    private UUID walletId;
    private UUID userId;
    private BigDecimal balance;
    private String currency;
    private String formattedBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private BigDecimal lowBalanceThreshold;
    private boolean isLowBalance;
    private Map<String, BigDecimal> multiCurrencyBalances;
    private LocalDateTime lastTransactionDate;
    private LocalDateTime lastUpdated;
    private String status;
}