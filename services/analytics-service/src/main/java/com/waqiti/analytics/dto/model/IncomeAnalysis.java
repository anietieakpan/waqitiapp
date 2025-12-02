package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Income analysis model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeAnalysis {
    private BigDecimal totalIncome;
    private BigDecimal averageDaily;
    private BigDecimal averageWeekly;
    private BigDecimal averageMonthly;
    private Map<String, BigDecimal> sourceBreakdown;
    private LocalDateTime peakIncomeDate;
    private BigDecimal peakIncomeAmount;
    private String topIncomeSource;
    
    // Additional fields needed
    private List<IncomeSource> incomeSources;
    private List<DailyIncome> dailyIncome;
    private BigDecimal averageDailyIncome;
    private String incomeStability;
    private BigDecimal incomeGrowthRate;
    private String primaryIncomeSource;
    private BigDecimal incomeConsistency;
}