package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Benchmarking Analysis DTO
 *
 * Compares user metrics against peer group averages and industry benchmarks.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkingAnalysis {

    private String peerGroup; // AGE_GROUP_25_34, INCOME_LEVEL_50K_75K, etc.

    private Map<String, BigDecimal> userMetrics;
    private Map<String, BigDecimal> peerAverages;
    private Map<String, String> comparisons; // ABOVE_AVERAGE, BELOW_AVERAGE, AVERAGE

    private String savingsComparison;
    private String spendingComparison;
    private Integer percentileRanking; // 0-100

    private java.util.List<String> insights;
}
