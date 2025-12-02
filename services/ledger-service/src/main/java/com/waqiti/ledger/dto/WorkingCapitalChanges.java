package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Working Capital Changes DTO
 * 
 * Tracks changes in working capital components
 * for indirect cash flow statement method
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingCapitalChanges {
    
    // Current asset changes
    private BigDecimal accountsReceivableChange;
    private BigDecimal inventoryChange;
    private BigDecimal prepaidExpensesChange;
    private BigDecimal shortTermInvestmentsChange;
    private BigDecimal otherCurrentAssetsChange;
    
    // Current liability changes
    private BigDecimal accountsPayableChange;
    private BigDecimal accruedLiabilitiesChange;
    private BigDecimal shortTermDebtChange;
    private BigDecimal taxesPayableChange;
    private BigDecimal otherCurrentLiabilitiesChange;
    
    // Calculated totals
    private BigDecimal totalCurrentAssetChange;
    private BigDecimal totalCurrentLiabilityChange;
    private BigDecimal netWorkingCapitalChange;
    
    // Analysis
    private BigDecimal workingCapitalTurnover;
    private String workingCapitalTrend; // IMPROVING, DETERIORATING, STABLE
    private String cashConversionEfficiency; // EFFICIENT, AVERAGE, INEFFICIENT
    
    // Period information
    private LocalDate startDate;
    private LocalDate endDate;
    private String currency;
    
    // Additional context
    private String commentary;
    private Boolean seasonalAdjusted;
}