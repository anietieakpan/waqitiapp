package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * API performance metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiMetrics {
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Double successRate;
    private Double avgResponseTime;
    private Map<String, Long> requestsByEndpoint;
    private Map<String, Double> latencyByEndpoint;
    private List<String> slowestEndpoints;
}