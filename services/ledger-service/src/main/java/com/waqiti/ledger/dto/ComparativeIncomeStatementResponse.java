package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comparative Income Statement Response DTO
 * 
 * Contains income statements for two periods with variance analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparativeIncomeStatementResponse {
    
    private IncomeStatementResponse currentPeriod;
    private IncomeStatementResponse priorPeriod;
    private Map<String, VarianceAnalysis> variances;
    private GrowthAnalysis growthAnalysis;
    private String analysisCommentary;
    private LocalDateTime generatedAt;
}