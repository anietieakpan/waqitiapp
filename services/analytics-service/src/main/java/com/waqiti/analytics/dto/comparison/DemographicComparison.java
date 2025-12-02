package com.waqiti.analytics.dto.comparison;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemographicComparison {
    private String ageGroup;
    private String incomeRange;
    private String location;
    private Map<String, BigDecimal> demographicBenchmarks;
    private BigDecimal spendingVsDemographic;
    private BigDecimal savingsVsDemographic;
    private String relativePosition; // TOP_PERFORMER, AVERAGE_PERFORMER, BELOW_AVERAGE
}