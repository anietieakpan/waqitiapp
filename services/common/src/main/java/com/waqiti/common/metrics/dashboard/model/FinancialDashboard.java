package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Financial performance dashboard metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDashboard {
    private LocalDateTime timestamp;
    private RevenueMetrics revenueMetrics;
    private BigDecimal totalVolume;
    private BigDecimal totalFees;
    private BigDecimal netRevenue;
    private Map<String, BigDecimal> currencyBreakdown;
    private Map<String, BigDecimal> channelBreakdown;
}