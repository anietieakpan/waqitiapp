package com.waqiti.analytics.dto.comparison;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerComparison {
    private Map<String, BigDecimal> categoryComparison; // Category -> percentile
    private BigDecimal spendingPercentile;
    private BigDecimal savingsPercentile;
    private String performanceLevel; // TOP_QUARTILE, ABOVE_AVERAGE, BELOW_AVERAGE, BOTTOM_QUARTILE
    private Integer peerGroupSize;
    private String comparisonCriteria; // AGE_INCOME, LOCATION, OCCUPATION
}