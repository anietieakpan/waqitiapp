package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResponse {
    private UUID accountId;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private BigDecimal effectiveCreditLimit;
    private BigDecimal totalAvailableFunds;
    private LocalDateTime lastUpdated;
}