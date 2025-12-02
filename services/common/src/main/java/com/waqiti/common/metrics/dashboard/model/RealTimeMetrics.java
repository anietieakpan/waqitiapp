package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Real-time metrics for live dashboard updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeMetrics {
    private LocalDateTime timestamp;
    private Long activeUsers;
    private Long transactionsPerSecond;
    private BigDecimal volumePerSecond;
    private Double avgResponseTime;
    private Map<String, Long> activeServices;
    private List<String> alerts;
}