package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cash Flow Analysis DTO - Enhanced with Validation
 *
 * Comprehensive cash flow analysis including net flow, trends, volatility,
 * and forecasting for liquidity management.
 *
 * Key Metrics:
 * - Net cash flow (positive = surplus, negative = deficit)
 * - Cash flow trend direction
 * - Volatility/stability assessment
 * - Days with positive/negative flow
 * - Future cash flow forecasts
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowAnalysis {

    /**
     * Net cash flow (income - spending)
     */
    @NotNull(message = "Net cash flow cannot be null")
    private BigDecimal netCashFlow;

    /**
     * Daily cash flow data points
     */
    @Valid
    private List<CashFlowData> cashFlowData;

    /**
     * Overall cash flow trend
     */
    private CashFlowTrend cashFlowTrend;

    /**
     * Average weekly cash flow
     */
    private BigDecimal averageWeeklyCashFlow;

    /**
     * Cash flow volatility score (0.0 - 1.0, higher = more volatile)
     */
    @PositiveOrZero(message = "Volatility score must be non-negative")
    private BigDecimal cashFlowVolatility;

    /**
     * Number of days with positive cash flow
     */
    @PositiveOrZero(message = "Positive flow days must be non-negative")
    private Integer positiveFlowDays;

    /**
     * Number of days with negative cash flow
     */
    @PositiveOrZero(message = "Negative flow days must be non-negative")
    private Integer negativeFlowDays;

    /**
     * Cash flow forecasts for upcoming periods
     */
    @Valid
    private List<CashFlowForecast> forecast;

    /**
     * Helper: Check if cash flow is healthy (positive net flow)
     */
    public boolean isHealthy() {
        return netCashFlow != null && netCashFlow.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Helper: Get cash flow health percentage (positive days / total days)
     */
    public BigDecimal getHealthPercentage() {
        int totalDays = (positiveFlowDays != null ? positiveFlowDays : 0) +
                        (negativeFlowDays != null ? negativeFlowDays : 0);
        if (totalDays == 0) return BigDecimal.ZERO;

        return BigDecimal.valueOf(positiveFlowDays != null ? positiveFlowDays : 0)
                .divide(BigDecimal.valueOf(totalDays), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
