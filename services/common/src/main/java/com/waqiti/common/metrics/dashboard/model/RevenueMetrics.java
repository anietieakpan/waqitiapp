package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Revenue metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueMetrics {
    private BigDecimal totalRevenue;
    private BigDecimal dailyRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal yearlyRevenue;
    private BigDecimal transactionFees;
    private BigDecimal subscriptionRevenue;
    private Double growthRate;
    private Map<String, BigDecimal> revenueBySource;
}