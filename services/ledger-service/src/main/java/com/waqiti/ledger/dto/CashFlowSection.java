package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cash Flow Section DTO
 * 
 * Represents a major section of the cash flow statement
 * (Operating, Investing, or Financing Activities)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowSection {
    
    private String sectionName;
    private List<CashFlowLineItem> lineItems;
    private BigDecimal netCashFlow;
    private String notes;
    
    // Analysis metrics
    private BigDecimal percentageOfTotalCash;
    private String trendDirection; // INCREASING, DECREASING, STABLE
    private String commentary;
}