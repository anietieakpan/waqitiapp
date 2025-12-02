package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * External service metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalServiceMetrics {
    private String serviceName;
    private Boolean available;
    private Double avgResponseTime;
    private Long totalRequests;
    private Long failedRequests;
    private Double successRate;
    private LocalDateTime lastHealthCheck;
    private Map<String, Object> metadata;
}