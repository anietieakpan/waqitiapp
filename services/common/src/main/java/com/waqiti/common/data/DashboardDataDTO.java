package com.waqiti.common.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dashboard Data DTO for comprehensive dashboard queries
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDataDTO {
    private String userId;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingTransactions;
    private int transactionCount7Days;
    private int transactionCount30Days;
    private BigDecimal spendingLast7Days;
    private BigDecimal spendingLast30Days;
    private List<TransactionDTO> recentTransactions;
    private List<String> alerts;
    private Map<String, BigDecimal> categorySpending;
    private Map<String, Object> accountSummary;
}