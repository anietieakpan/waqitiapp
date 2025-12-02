package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.PerformanceMetrics;
import com.waqiti.reconciliation.domain.TrendData;
import com.waqiti.reconciliation.dto.AnalyticsRequestDto;

import java.util.List;

public interface MetricsCalculationService {
    
    /**
     * Calculate performance metrics for given time period
     */
    PerformanceMetrics calculatePerformanceMetrics(AnalyticsRequestDto request);
    
    /**
     * Calculate trend data for specific metrics
     */
    List<TrendData> calculateTrendData(AnalyticsRequestDto request);
    
    /**
     * Calculate system performance baseline
     */
    PerformanceMetrics calculateBaseline(String timeFrame);
}