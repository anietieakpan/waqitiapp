package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthResult {
    private LocalDateTime timestamp;
    private SystemStatus overallStatus;
    private List<ComponentHealthCheck> componentChecks;
    private Map<String, Object> metrics;
    private String summaryMessage;
    private Double overallScore;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ComponentHealthCheck {
    private String component;
    private HealthStatus status;
    private java.time.Duration responseTime;
    private Map<String, Object> details;
    private String errorMessage;
    private LocalDateTime lastCheckedAt;
}

enum SystemStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    MAINTENANCE,
    UNKNOWN
}

enum HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNREACHABLE
}