package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SystemHealth {
    private final HealthStatus overallStatus;
    private final BigDecimal healthScore; // 0-100 scale
    private final List<HealthCheck> healthChecks;
    private final Map<String, String> systemMetrics;
    private final LocalDateTime lastUpdated;
    private final String statusMessage;
    
    @Data
    @Builder
    public static class HealthCheck {
        private final String serviceName;
        private final HealthStatus status;
        private final String message;
        private final Long responseTimeMs;
        private final LocalDateTime checkedAt;
    }
    
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
    
    public boolean isCritical() {
        return overallStatus == HealthStatus.UNHEALTHY;
    }
    
    public boolean requiresAttention() {
        return overallStatus == HealthStatus.DEGRADED || overallStatus == HealthStatus.UNHEALTHY;
    }
}