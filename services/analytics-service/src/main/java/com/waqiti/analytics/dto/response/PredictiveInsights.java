package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Predictive Insights DTO
 *
 * AI/ML-powered predictions and insights for future financial behavior.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictiveInsights {

    private List<ForecastData> spendingForecasts;
    private List<ForecastData> incomeForecasts;
    private BigDecimal predictedMonthlySpending;
    private BigDecimal predictedMonthlyIncome;
    private BigDecimal predictedSavings;

    private String cashFlowOutlook; // POSITIVE, NEUTRAL, NEGATIVE
    private Integer daysUntilCashShortfall;
    private BigDecimal recommendedSavingsAmount;

    private List<String> upcomingExpenses;
    private List<String> predictedLifeEvents; // Based on spending patterns
}
