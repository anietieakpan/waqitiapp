package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO for spending analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpendingAnalytics {

    private String period; // WEEKLY, MONTHLY, QUARTERLY, YEARLY

    private BigDecimal currentPeriodSpending;

    private BigDecimal previousPeriodSpending;

    private BigDecimal percentageChange;

    private String currency;

    private Map<String, BigDecimal> spendingByCategory;

    private Map<String, BigDecimal> spendingByBiller;

    private List<MonthlySpendingDto> monthlyBreakdown;

    private BigDecimal averageMonthlySpending;

    private BigDecimal highestMonthSpending;

    private BigDecimal lowestMonthSpending;

    private List<String> spendingInsights; // AI-generated insights

    private List<String> savingsRecommendations;
}
