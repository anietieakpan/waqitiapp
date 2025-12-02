package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Predictive insights model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictiveInsights {
    private BigDecimal nextMonthPredictedSpending;
    private BigDecimal nextMonthPredictedIncome;
    private Map<String, BigDecimal> categoryPredictions;
    private List<PredictedEvent> upcomingEvents;
    private BigDecimal cashFlowPrediction;
    private String spendingTrendPrediction; // INCREASING, STABLE, DECREASING
    private BigDecimal confidenceScore;
    private List<String> riskWarnings;
    private Map<String, BigDecimal> seasonalAdjustments;
}