package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cash flow analysis model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowAnalysis {
    private BigDecimal netCashFlow;
    private List<CashFlowData> cashFlowData;
    private String cashFlowTrend;
    private BigDecimal averageWeeklyCashFlow;
    private BigDecimal cashFlowVolatility;
    private Integer positiveFlowDays;
    private Integer negativeFlowDays;
    private List<CashFlowForecast> forecast;
}