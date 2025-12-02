package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comparative Balance Sheet Response DTO
 * 
 * Contains balance sheets for two periods with variance analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparativeBalanceSheetResponse {
    
    private BalanceSheetResponse currentPeriod;
    private BalanceSheetResponse priorPeriod;
    private Map<String, VarianceAnalysis> variances;
    private String analysisCommentary;
    private LocalDateTime generatedAt;
}