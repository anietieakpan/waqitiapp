package com.waqiti.analytics.dto.comparison;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndustryComparison {
    private String industry;
    private Map<String, BigDecimal> benchmarks; // Metric -> industry average
    private BigDecimal userVsIndustrySpending;
    private BigDecimal userVsIndustrySavings;
    private String performance; // ABOVE_INDUSTRY, AT_INDUSTRY, BELOW_INDUSTRY
}