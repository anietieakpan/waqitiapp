package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reconciliation Variance DTO
 * 
 * Contains detailed analysis of reconciliation variances
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationVariance {
    
    // Balance comparison
    private BigDecimal bookBalance;
    private BigDecimal reconciledBalance;
    private BigDecimal totalVariance;
    
    // Variance analysis
    private Boolean withinTolerance;
    private BigDecimal toleranceThreshold;
    private BigDecimal variancePercentage;
    
    // Variance categorization
    private List<VarianceBreakdown> varianceBreakdowns;
    private BigDecimal timingDifferences;
    private BigDecimal bankErrors;
    private BigDecimal bookErrors;
    private BigDecimal unknownVariances;
    
    // Risk assessment
    private String riskLevel; // LOW, MEDIUM, HIGH
    private Boolean requiresInvestigation;
    private String explanation;
    
    // Recommendations
    private List<String> recommendations;
    private String nextSteps;
    
    // Historical context
    private BigDecimal averageVarianceLast12Months;
    private String varianceTrend; // IMPROVING, STABLE, DETERIORATING
}