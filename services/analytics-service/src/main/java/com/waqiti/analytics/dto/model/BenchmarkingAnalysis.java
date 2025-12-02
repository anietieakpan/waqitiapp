package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Benchmarking analysis model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkingAnalysis {
    private String peerGroup;
    private Map<String, BigDecimal> userMetrics;
    private Map<String, BigDecimal> peerAverages;
    private Map<String, BigDecimal> percentileRanks;
    private BigDecimal overallPercentile;
    private Map<String, String> insights;
    private Map<String, BigDecimal> improvementAreas;
    private Integer peerGroupSize;
}