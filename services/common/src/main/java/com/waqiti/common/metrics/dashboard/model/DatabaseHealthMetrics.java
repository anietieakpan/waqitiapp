package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Comprehensive database health metrics for production monitoring
 * Tracks connection pools, query performance, and database health indicators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseHealthMetrics {
    // Connection pool metrics
    private Long connectionPoolSize;
    private Long activeConnections;
    private Long maxConnections;
    private Long idleConnections;
    private Long pendingConnections;
    private Double connectionUtilization;
    private Long connectionWaitTime;
    private Long connectionTimeouts;
    
    // Query performance metrics
    private Long slowQueries;
    private Double avgQueryTime;
    private Double maxQueryTime;
    private Double minQueryTime;
    private Map<String, Double> queryTimePercentiles;
    private Long totalQueries;
    private Long failedQueries;
    
    // Database health indicators
    private Long deadlocks;
    private Double replicationLag;
    private Long transactionRollbacks;
    private Double cacheHitRatio;
    private Long tableLocks;
    private Double diskUsagePercent;
    private Long openCursors;
    
    // Detailed statistics
    private Map<String, Object> poolStats;
    private Map<String, Long> tableStats;
    private Map<String, Double> performanceSchema;
    private Map<String, Object> replicationStatus;
}