package com.waqiti.common.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of testing a database connection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {
    
    /**
     * Name of the data source
     */
    private String dataSourceName;
    
    /**
     * Whether the connection test was successful
     */
    private boolean success;
    
    /**
     * Connection test status
     */
    private ConnectionStatus status;
    
    /**
     * Response time in milliseconds
     */
    private Long responseTimeMs;
    
    /**
     * Error message if test failed
     */
    private String errorMessage;
    
    /**
     * Exception class name if failed
     */
    private String exceptionType;
    
    /**
     * Database product name
     */
    private String databaseProductName;
    
    /**
     * Database product version
     */
    private String databaseProductVersion;
    
    /**
     * JDBC driver name
     */
    private String driverName;
    
    /**
     * JDBC driver version
     */
    private String driverVersion;
    
    /**
     * Connection URL (sanitized)
     */
    private String connectionUrl;
    
    /**
     * Current pool statistics
     */
    private PoolStatistics poolStats;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Test timestamp
     */
    private LocalDateTime testedAt;
    
    /**
     * Connection status enumeration
     */
    public enum ConnectionStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        TIMEOUT,
        ERROR
    }
    
    /**
     * Pool statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoolStatistics {
        private Integer totalConnections;
        private Integer activeConnections;
        private Integer idleConnections;
        private Integer threadsAwaitingConnection;
        private Long maxConnectionWaitTimeMs;
        private Double averageConnectionWaitTimeMs;
        private Integer maxPoolSize;
        private Integer minPoolSize;
    }
    
    /**
     * Check if connection is healthy
     */
    public boolean isHealthy() {
        return success && status == ConnectionStatus.HEALTHY;
    }
    
    /**
     * Check if connection needs attention
     */
    public boolean needsAttention() {
        return status == ConnectionStatus.DEGRADED || 
               (poolStats != null && poolStats.getThreadsAwaitingConnection() > 5);
    }
    
    /**
     * Get health summary
     */
    public String getHealthSummary() {
        if (isHealthy()) {
            return String.format("%s: Healthy (Response: %dms)", dataSourceName, responseTimeMs);
        } else {
            return String.format("%s: %s - %s", dataSourceName, status, 
                errorMessage != null ? errorMessage : "Connection issue detected");
        }
    }
}