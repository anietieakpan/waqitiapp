package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatisticsResponse {
    private long totalAccounts;
    private long activeAccounts;
    private long frozenAccounts;
    private long closedAccounts;
    private Map<String, Long> accountsByType;
    private Map<String, Long> accountsByCurrency;
    private BigDecimal totalBalance;
}