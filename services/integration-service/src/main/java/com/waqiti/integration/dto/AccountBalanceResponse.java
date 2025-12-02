package com.waqiti.integration.dto;

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
public class AccountBalanceResponse {
    private String accountId;
    private String userId;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private String currency;
    private Instant lastUpdated;
}