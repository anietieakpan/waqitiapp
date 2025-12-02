package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Financial Ratios DTO
 * 
 * Contains key financial ratios and performance metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialRatios {
    
    // Profitability ratios
    private BigDecimal grossMargin;
    private BigDecimal operatingMargin;
    private BigDecimal netMargin;
    private BigDecimal returnOnSales;
    
    // Efficiency ratios
    private BigDecimal operatingRatio;
    private BigDecimal expenseRatio;
    
    // Growth ratios
    private BigDecimal revenueGrowthRate;
    private BigDecimal profitGrowthRate;
    
    // Other metrics
    private BigDecimal ebitdaMargin;
    private BigDecimal operatingLeverage;
}