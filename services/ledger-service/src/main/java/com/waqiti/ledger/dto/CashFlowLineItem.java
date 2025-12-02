package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash Flow Line Item DTO
 * 
 * Represents individual line items within cash flow statement sections
 * with detailed account information and analysis metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowLineItem {
    
    // Account information
    private UUID accountId;
    private String accountCode;
    private String description;
    private BigDecimal amount;
    private String currency;
    
    // Categorization
    private String category; // INFLOW, OUTFLOW
    private String activityType; // OPERATING, INVESTING, FINANCING
    
    // Analysis and comparison
    private BigDecimal priorPeriodAmount;
    private BigDecimal variance;
    private BigDecimal percentageChange;
    private BigDecimal percentageOfSection;
    
    // Additional context
    private String notes;
    private Boolean isSubTotal;
    private Integer indentLevel;
    
    // Supporting details
    private String calculationMethod;
    private Boolean isEstimate;
    private String dataSource;
}