package com.waqiti.expense.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Expense analytics response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseAnalyticsResponse {

    private BigDecimal totalExpenses;

    private BigDecimal averageExpense;

    private BigDecimal highestExpense;

    private BigDecimal lowestExpense;

    private Integer expenseCount;

    private Map<String, BigDecimal> expensesByCategory;

    private Map<String, BigDecimal> expensesByMerchant;

    private Map<String, Integer> transactionCountByCategory;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private Map<String, BigDecimal> monthlyTrend;

    private String topCategory;

    private String topMerchant;

    private BigDecimal projectedMonthlyExpense;
}
