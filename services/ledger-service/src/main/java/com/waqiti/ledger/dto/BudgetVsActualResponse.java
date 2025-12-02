package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for budget vs actual analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetVsActualResponse {
    
    private UUID budgetId;
    private String budgetName;
    private LocalDate asOfDate;
    
    private List<BudgetVsActualLineItem> lineItems;
    
    private BigDecimal totalBudget;
    private BigDecimal totalActual;
    private BigDecimal totalVariance;
    private BigDecimal overallUtilization;
    
    private List<BudgetInsight> insights;
    private List<String> recommendations;
    
    private LocalDateTime generatedAt;
    
    // Summary statistics
    private Integer totalLineItems;
    private Integer overBudgetItems;
    private Integer atRiskItems;
    private Integer onTrackItems;
    private Integer underUtilizedItems;
    
    // Performance indicators
    private String overallStatus;
    private String healthRating;
    private BigDecimal efficiencyScore;
}