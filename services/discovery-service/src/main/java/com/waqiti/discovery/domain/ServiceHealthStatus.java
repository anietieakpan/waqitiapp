package com.waqiti.discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Value object representing the health status of a service
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthStatus {
    private String serviceName;
    private HealthStatus status;
    private int healthyInstances;
    private int totalInstances;
    private Instant lastCheckTime;
    private List<InstanceHealthCheckResult> details;
    private String message;
    private Double healthScore;

    /**
     * Calculate health percentage
     *
     * @return health percentage (0-100)
     */
    public double getHealthPercentage() {
        if (totalInstances == 0) {
            return 0.0;
        }
        return (double) healthyInstances / totalInstances * 100.0;
    }

    /**
     * Check if service is considered healthy
     *
     * @return true if at least 50% of instances are healthy
     */
    public boolean isServiceHealthy() {
        return getHealthPercentage() >= 50.0;
    }
}
