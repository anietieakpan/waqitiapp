package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceSummary {
    private UUID accountId;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private long monthlyDebitCount;
    private long monthlyCreditCount;
    private BigDecimal monthlyDebitAmount;
    private BigDecimal monthlyCreditAmount;
    private LocalDateTime lastCalculated;
    private String currency;
}