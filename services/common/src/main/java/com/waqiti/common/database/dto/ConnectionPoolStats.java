package com.waqiti.common.database.dto;

import lombok.Data;

/**
 * Connection pool statistics for monitoring database connections.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class ConnectionPoolStats {
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int maxConnections;
    private long waitTime;
    private double utilizationRate;
}