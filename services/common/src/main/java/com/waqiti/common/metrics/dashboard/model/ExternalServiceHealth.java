package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * External service health monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalServiceHealth {
    private String overallHealth;
    private Map<String, String> serviceStatus;
    private Map<String, ServiceHealth> services;
    private Integer healthyServices;
    private Integer unhealthyServices;
    private Integer degradedServices;
    private LocalDateTime lastCheck;
    private List<String> criticalServices;
}