package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents the health status of a database connection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionHealthStatus {
    private String connectionId;
    private String poolName;
    private HealthState state;
    private Instant lastChecked;
    private Instant lastUsed;
    private Instant testTime;
    private long totalQueriesExecuted;
    private long totalErrors;
    private double averageQueryTimeMs;
    private String lastError;
    private Instant lastErrorTime;
    private boolean isValid;
    private boolean healthy;
    private int validationTimeoutSeconds;
    private long connectionTimeMs;
    private long queryTimeMs;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int threadsAwaitingConnection;
    private List<String> issues;
    
    public enum HealthState {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
}