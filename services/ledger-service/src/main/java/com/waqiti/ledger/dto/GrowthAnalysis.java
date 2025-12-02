package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Growth Analysis DTO
 * 
 * Contains growth rate analysis for key financial metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthAnalysis {
    
    private BigDecimal revenueGrowth;
    private BigDecimal grossProfitGrowth;
    private BigDecimal operatingIncomeGrowth;
    private BigDecimal netIncomeGrowth;
    private BigDecimal operatingExpenseGrowth;
    
    // Trend analysis
    private String trendDirection; // IMPROVING, DECLINING, STABLE
    private Boolean sustainableGrowth;
    private String commentary;
}