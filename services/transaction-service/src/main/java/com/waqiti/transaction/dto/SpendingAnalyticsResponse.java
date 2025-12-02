package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingAnalyticsResponse {
    
    private String period;
    private String walletId;
    private BigDecimal totalSpending;
    private Integer transactionCount;
    private BigDecimal averageTransactionAmount;
    private BigDecimal maxTransactionAmount;
    private Map<String, BigDecimal> spendingByCategory;
    private Map<String, Integer> transactionCountByCategory;
    private Map<LocalDate, BigDecimal> dailySpending;
    private Map<String, BigDecimal> topMerchants;
    private LocalDate startDate;
    private LocalDate endDate;
}