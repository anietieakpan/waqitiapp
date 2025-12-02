package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive service health information for enterprise monitoring
 * Provides detailed health metrics for distributed services
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealth {
    private String serviceName;
    private String status;
    private Boolean healthy;
    private LocalDateTime lastHealthCheck;
    private Long uptime;
    private Double avgResponseTime;
    private Map<String, Object> healthDetails;
    
    // Additional comprehensive health fields
    private String overallStatus;
    private Map<String, String> services;
    private Map<String, Object> dependencies;
    private Integer healthScore;
    private String environment;
    private String version;
    private Map<String, Long> resourceUsage;
    private Map<String, Double> performanceMetrics;
    private List<String> activeAlerts;
    private LocalDateTime startupTime;
    private Long totalRequests;
    private Long failedRequests;
    private Double successRate;
    private Map<String, Integer> errorCounts;
    private Map<String, Object> customMetrics;
}