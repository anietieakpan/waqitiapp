package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cash Flow Statement Response DTO
 * 
 * Contains comprehensive cash flow statement with operating, investing, 
 * and financing activities, plus analysis and forecasting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowStatementResponse {
    
    // Period information
    private LocalDate startDate;
    private LocalDate endDate;
    private String method; // DIRECT or INDIRECT
    private String currency;
    
    // Cash flow sections
    private CashFlowSection operatingActivities;
    private CashFlowSection investingActivities;
    private CashFlowSection financingActivities;
    
    // Summary totals
    private BigDecimal netOperatingCashFlow;
    private BigDecimal netInvestingCashFlow;
    private BigDecimal netFinancingCashFlow;
    private BigDecimal netCashFlow;
    
    // Cash balances
    private BigDecimal beginningCash;
    private BigDecimal endingCash;
    
    // Analysis and metrics
    private BigDecimal freeCashFlow;
    private CashFlowAnalysis analysis;
    
    // Metadata
    private LocalDateTime generatedAt;
    private String preparedBy;
    private String notes;
}