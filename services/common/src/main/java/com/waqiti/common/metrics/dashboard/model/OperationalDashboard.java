package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Operational health dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalDashboard {
    private LocalDateTime timestamp;
    private ServiceHealth serviceHealth;
    private DatabaseHealthMetrics databaseHealth;
    private CachePerformanceMetrics cachePerformance;
    private SystemResourceUsage resourceUsage;
    private UptimeMetrics uptimeMetrics;
    private AlertSummary alertSummary;
}