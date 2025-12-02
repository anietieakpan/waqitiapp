package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash Flow Forecast DTO
 *
 * Predicted cash flow for future periods based on
 * historical patterns and trends.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast {

    // Forecast Period
    private LocalDate forecastDate;
    private String periodLabel; // e.g., "2025-11", "2025-W45"
    private String periodType; // "DAILY", "WEEKLY", "MONTHLY"

    // Predicted Values
    private BigDecimal predictedInflow;
    private BigDecimal predictedOutflow;
    private BigDecimal predictedNetCashFlow;
    private BigDecimal predictedBalance;

    // Confidence Intervals
    private BigDecimal lowEstimate;
    private BigDecimal highEstimate;
    private BigDecimal confidenceLevel; // 0.0-1.0 (e.g., 0.95 = 95% confidence)
    private String confidence; // "HIGH", "MEDIUM", "LOW"

    // Variance
    private BigDecimal standardDeviation;
    private BigDecimal predictionError; // Expected error margin

    // Factors Influencing Forecast
    private Boolean includesRecurringIncome;
    private Boolean includesRecurringExpenses;
    private Boolean includesSeasonalAdjustment;
    private Boolean includesTrendAdjustment;

    // Assumptions
    private String forecastMethod; // "LINEAR_REGRESSION", "MOVING_AVERAGE", "EXPONENTIAL_SMOOTHING", "ARIMA"
    private Integer historicalDataPoints; // Number of data points used
    private LocalDate trainingStartDate;
    private LocalDate trainingEndDate;

    // Alerts
    private Boolean isPredictedNegative;
    private Boolean isHighVolatility;
    private Boolean requiresAttention;
    private String alert; // Human-readable alert message

    // Metadata
    private String currency;
    private LocalDate generatedAt;
    private Integer modelVersion;

    // Helper Methods
    public boolean isHighConfidence() {
        return "HIGH".equals(confidence) ||
                (confidenceLevel != null && confidenceLevel.compareTo(BigDecimal.valueOf(0.85)) > 0);
    }

    public boolean isPredictedPositive() {
        return predictedNetCashFlow != null && predictedNetCashFlow.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getConfidenceRange() {
        if (highEstimate == null || lowEstimate == null) {
            return BigDecimal.ZERO;
        }
        return highEstimate.subtract(lowEstimate);
    }
}
