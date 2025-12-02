package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceResponse {
    @NotNull
    private UUID walletId;
    @NotNull
    private UUID userId;
    @NotNull
    private BigDecimal balance;
    @NotNull
    private String currency;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private BigDecimal pendingBalance;
    private String walletStatus;
    private LocalDateTime lastTransactionAt;
    private LocalDateTime balanceAsOf;
}
