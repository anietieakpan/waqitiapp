package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for comprehensive budget variance analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetVarianceAnalysis {
    
    private UUID budgetId;
    private String budgetName;
    private LocalDate analysisStartDate;
    private LocalDate analysisEndDate;
    
    private Map<String, CategoryVariance> categoryVariances;
    private Map<String, MonthlyVariance> monthlyVariances;
    
    private List<TopVarianceItem> topPositiveVariances;
    private List<TopVarianceItem> topNegativeVariances;
    
    private VarianceTrend varianceTrend;
    private List<VarianceExplanation> explanations;
    
    private LocalDateTime generatedAt;
    
    // Summary metrics
    private String overallVarianceTrend;
    private String riskLevel;
    private List<String> keyFindings;
    private List<String> recommendations;
}