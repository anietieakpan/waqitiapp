package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * System metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetrics {
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private Long activeConnections;
    private Long requestsPerSecond;
    private Double avgResponseTime;
    private Map<String, Double> serviceLatency;
    private Map<String, Long> errorCounts;
}