package com.waqiti.discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Value object representing health check result for a service instance
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceHealthCheckResult {
    private String instanceId;
    private String host;
    private int port;
    private boolean healthy;
    private HealthStatus status;
    private long responseTime;
    private Integer statusCode;
    private String message;
    private String errorMessage;
    private Instant checkTime;
    private int consecutiveFailures;
    private int consecutiveSuccesses;

    /**
     * Check if instance passed health check
     *
     * @return true if healthy
     */
    public boolean isPassed() {
        return healthy && statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * Check if instance should be marked as down
     *
     * @param threshold number of consecutive failures before marking down
     * @return true if should be marked down
     */
    public boolean shouldMarkDown(int threshold) {
        return consecutiveFailures >= threshold;
    }
}
