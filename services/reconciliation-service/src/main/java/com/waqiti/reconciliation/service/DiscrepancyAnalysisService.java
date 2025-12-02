package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.DiscrepancyAnalysis;
import com.waqiti.reconciliation.dto.AnalyticsRequestDto;

import java.util.List;

public interface DiscrepancyAnalysisService {
    
    /**
     * Analyze discrepancies for given time period
     */
    DiscrepancyAnalysis analyzeDiscrepancies(AnalyticsRequestDto request);
    
    /**
     * Categorize discrepancies by type and impact
     */
    List<DiscrepancyAnalysis.DiscrepancyCategory> categorizeDiscrepancies(AnalyticsRequestDto request);
    
    /**
     * Generate root cause analysis
     */
    List<String> performRootCauseAnalysis(AnalyticsRequestDto request);
    
    /**
     * Generate recommendations for discrepancy reduction
     */
    List<String> generateRecommendations(DiscrepancyAnalysis analysis);
}