package com.waqiti.analytics.dto.response;

import com.waqiti.analytics.dto.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive analytics response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprehensiveAnalyticsResponse {
    private UUID userId;
    private LocalDateTime generatedAt;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    // Summary metrics
    private TransactionSummary transactionSummary;
    private SpendingAnalysis spendingAnalysis;
    private IncomeAnalysis incomeAnalysis;
    
    // Detailed analysis
    private List<CategorySpending> categorySpending;
    private List<DailySpending> dailySpending;
    private List<HourlySpending> hourlySpending;
    
    // Behavioral insights
    private List<SpendingPattern> spendingPatterns;
    private BehaviorInsights behaviorInsights;
    
    // Predictive analytics
    private List<ForecastData> forecasts;
    private List<AnomalyDetection> anomalies;
    
    // Risk assessment
    private RiskAssessment riskAssessment;
    
    // Additional metadata
    private Map<String, Object> metadata;
}