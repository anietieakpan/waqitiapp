package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cash Flow Analysis DTO
 * 
 * Contains comprehensive analysis of cash flow patterns,
 * trends, and financial health indicators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowAnalysis {
    
    // Trend analysis
    private String operatingCashFlowTrend; // POSITIVE, NEGATIVE, IMPROVING, DECLINING
    private String investingCashFlowTrend;
    private String financingCashFlowTrend;
    
    // Key metrics
    private BigDecimal freeCashFlow;
    private BigDecimal operatingCashFlowRatio;
    private BigDecimal cashFlowToDebtRatio;
    private BigDecimal cashFlowCoverage;
    
    // Quality indicators
    private String cashFlowStrength; // STRONG, MODERATE, WEAK
    private BigDecimal cashConversionCycle;
    private String liquidityPosition; // EXCELLENT, GOOD, ADEQUATE, POOR
    
    // Forecasting
    private Map<String, BigDecimal> projectedCashFlow;
    private String seasonalityPattern;
    private BigDecimal volatilityIndex;
    
    // Commentary and insights
    private String commentary;
    private String keyInsights;
    private String recommendedActions;
    
    // Risk assessment
    private String cashFlowRisk; // LOW, MODERATE, HIGH
    private String workingCapitalTrend;
    private Boolean sustainableCashFlow;
}