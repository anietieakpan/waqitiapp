package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO representing a single line item in budget vs actual comparison
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetVsActualLineItem {
    
    private UUID lineItemId;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String category;
    private String subcategory;
    
    private BigDecimal budgetAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BigDecimal variancePercentage;
    private BigDecimal utilization;
    
    private String status; // ON_TRACK, AT_RISK, OVER_BUDGET, UNDER_UTILIZED
    private String priority;
    
    // Trend analysis
    private BigDecimal previousPeriodActual;
    private BigDecimal monthOverMonthChange;
    private String trend; // INCREASING, DECREASING, STABLE
    
    // Forecasting
    private BigDecimal projectedYearEnd;
    private BigDecimal projectedVariance;
    
    // Additional context
    private String notes;
    private String explanation;
    private String recommendedAction;
}