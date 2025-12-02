package com.waqiti.analytics.dto;

import com.waqiti.analytics.model.AnomalyDetectionResult;
import com.waqiti.analytics.model.TransactionStats;
import com.waqiti.analytics.model.TrendingPattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for analytics dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDashboardResponse {
    
    private TransactionStats transactionStats;
    private List<TrendingPattern> trendingPatterns;
    private List<AnomalyDetectionResult> recentAnomalies;
    private Integer timeRange;
    private Instant generatedAt;
}