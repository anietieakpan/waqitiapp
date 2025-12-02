package com.waqiti.common.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.waqiti.common.monitoring.ConnectionPoolMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive database connection pool monitoring
 * Tracks connection usage, performance, and health
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabasePoolMonitor implements HealthIndicator {
    
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, ConnectionPoolMetrics> metricsHistory = new ConcurrentHashMap<>();
    private HikariPoolMXBean poolMXBean;
    
    @Override
    public Health health() {
        try {
            ConnectionPoolMetrics metrics = getCurrentMetrics();
            
            if (metrics == null) {
                return Health.down()
                    .withDetail("error", "Unable to retrieve pool metrics")
                    .build();
            }
            
            Health.Builder builder = metrics.isHealthy() ? Health.up() : Health.down();
            
            return builder
                .withDetail("activeConnections", metrics.getActiveConnections())
                .withDetail("idleConnections", metrics.getIdleConnections())
                .withDetail("totalConnections", metrics.getTotalConnections())
                .withDetail("threadsAwaitingConnection", metrics.getThreadsAwaitingConnection())
                .withDetail("connectionWaitTime", metrics.getAverageConnectionWaitTime() + "ms")
                .withDetail("connectionUtilization", String.format("%.2f%%", metrics.getUtilizationPercentage()))
                .withDetail("status", metrics.getHealthStatus())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to check database pool health", e);
            return Health.down()
                .withException(e)
                .build();
        }
    }
    
    /**
     * Monitor connection pool metrics every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void monitorConnectionPool() {
        try {
            ConnectionPoolMetrics metrics = getCurrentMetrics();
            
            if (metrics != null) {
                recordMetrics(metrics);
                checkForIssues(metrics);
                storeMetricsHistory(metrics);
            }
            
        } catch (Exception e) {
            log.error("Error monitoring connection pool", e);
        }
    }
    
    /**
     * Get current connection pool metrics
     */
    public ConnectionPoolMetrics getCurrentMetrics() {
        try {
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                
                if (poolMXBean == null) {
                    poolMXBean = hikariDataSource.getHikariPoolMXBean();
                }
                
                if (poolMXBean != null) {
                    return ConnectionPoolMetrics.builder()
                        .timestamp(LocalDateTime.now())
                        .activeConnections(poolMXBean.getActiveConnections())
                        .idleConnections(poolMXBean.getIdleConnections())
                        .totalConnections(poolMXBean.getTotalConnections())
                        .threadsAwaitingConnection(poolMXBean.getThreadsAwaitingConnection())
                        .maximumPoolSize(hikariDataSource.getMaximumPoolSize())
                        .minimumIdle(hikariDataSource.getMinimumIdle())
                        .connectionTimeout(hikariDataSource.getConnectionTimeout())
                        .idleTimeout(hikariDataSource.getIdleTimeout())
                        .maxLifetime(hikariDataSource.getMaxLifetime())
                        .poolName(hikariDataSource.getPoolName())
                        .build();
                }
            }
            
            // Fallback for non-Hikari datasources
            return getBasicMetrics();
            
        } catch (Exception e) {
            log.error("Failed to get connection pool metrics", e);
            return null;
        }
    }
    
    /**
     * Get basic metrics for non-Hikari datasources
     */
    private ConnectionPoolMetrics getBasicMetrics() {
        int activeCount = 0;
        boolean canConnect = false;
        
        try (Connection conn = dataSource.getConnection()) {
            canConnect = conn.isValid(5);
            activeCount = 1; // At least one connection is available
        } catch (SQLException e) {
            log.warn("Failed to get basic connection metrics", e);
        }
        
        return ConnectionPoolMetrics.builder()
            .timestamp(LocalDateTime.now())
            .activeConnections(activeCount)
            .totalConnections(activeCount)
            .canConnect(canConnect)
            .build();
    }
    
    /**
     * Record metrics to Micrometer
     */
    private void recordMetrics(ConnectionPoolMetrics metrics) {
        Tags tags = Tags.of("pool", metrics.getPoolName() != null ? metrics.getPoolName() : "default");
        
        meterRegistry.gauge("database.pool.connections.active", tags, metrics.getActiveConnections());
        meterRegistry.gauge("database.pool.connections.idle", tags, metrics.getIdleConnections());
        meterRegistry.gauge("database.pool.connections.total", tags, metrics.getTotalConnections());
        meterRegistry.gauge("database.pool.connections.pending", tags, metrics.getThreadsAwaitingConnection());
        meterRegistry.gauge("database.pool.utilization", tags, metrics.getUtilizationPercentage());
        
        if (metrics.getAverageConnectionWaitTime() > 0) {
            meterRegistry.timer("database.pool.connection.wait", tags)
                .record(metrics.getAverageConnectionWaitTime(), TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Check for connection pool issues
     */
    private void checkForIssues(ConnectionPoolMetrics metrics) {
        // Check for high utilization
        if (metrics.getUtilizationPercentage() > 80) {
            log.warn("Database connection pool utilization is high: {}%", 
                String.format("%.2f", metrics.getUtilizationPercentage()));
        }
        
        // Check for connection starvation
        if (metrics.getThreadsAwaitingConnection() > 5) {
            log.error("Connection pool starvation detected! {} threads waiting for connections",
                metrics.getThreadsAwaitingConnection());
        }
        
        // Check for exhausted pool
        if (metrics.getIdleConnections() == 0 && 
            metrics.getActiveConnections() >= metrics.getMaximumPoolSize()) {
            log.error("Connection pool exhausted! No idle connections available");
        }
        
        // Check for connection leaks
        if (metrics.getActiveConnections() > metrics.getMaximumPoolSize() * 0.9) {
            log.warn("Possible connection leak detected. Active connections: {}/{}", 
                metrics.getActiveConnections(), metrics.getMaximumPoolSize());
        }
    }
    
    /**
     * Store metrics history for trend analysis
     */
    private void storeMetricsHistory(ConnectionPoolMetrics metrics) {
        String key = LocalDateTime.now().toString();
        metricsHistory.put(key, metrics);
        
        // Keep only last 100 entries
        if (metricsHistory.size() > 100) {
            metricsHistory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(metricsHistory.size() - 100)
                .forEach(entry -> metricsHistory.remove(entry.getKey()));
        }
    }
    
    /**
     * Get connection pool statistics
     */
    public ConnectionPoolStatistics getStatistics() {
        ConnectionPoolMetrics current = getCurrentMetrics();
        
        if (current == null) {
            return ConnectionPoolStatistics.builder()
                .available(false)
                .build();
        }
        
        double avgUtilization = metricsHistory.values().stream()
            .mapToDouble(ConnectionPoolMetrics::getUtilizationPercentage)
            .average()
            .orElse(current.getUtilizationPercentage());
            
        int maxActiveConnections = metricsHistory.values().stream()
            .mapToInt(ConnectionPoolMetrics::getActiveConnections)
            .max()
            .orElse(current.getActiveConnections());
            
        long avgWaitTime = metricsHistory.values().stream()
            .mapToLong(ConnectionPoolMetrics::getAverageConnectionWaitTime)
            .filter(time -> time > 0)
            .average()
            .stream()
            .mapToLong(Math::round)
            .findFirst()
            .orElse(0L);
            
        return ConnectionPoolStatistics.builder()
            .available(true)
            .current(current)
            .averageUtilization(avgUtilization)
            .maxActiveConnectionsObserved(maxActiveConnections)
            .averageConnectionWaitTime(avgWaitTime)
            .healthStatus(current.getHealthStatus())
            .recommendations(generateRecommendations(current, avgUtilization))
            .build();
    }
    
    /**
     * Generate recommendations based on metrics
     */
    private String[] generateRecommendations(ConnectionPoolMetrics current, double avgUtilization) {
        var recommendations = new java.util.ArrayList<String>();
        
        if (avgUtilization > 70) {
            recommendations.add("Consider increasing maximum pool size");
        }
        
        if (current.getThreadsAwaitingConnection() > 0) {
            recommendations.add("Threads are waiting for connections - increase pool size or optimize queries");
        }
        
        if (current.getIdleConnections() > current.getMaximumPoolSize() * 0.5) {
            recommendations.add("Many idle connections - consider reducing minimum idle setting");
        }
        
        if (current.getAverageConnectionWaitTime() > 1000) {
            recommendations.add("High connection wait time - optimize query performance");
        }
        
        return recommendations.toArray(new String[0]);
    }
    
    /**
     * Test database connectivity
     */
    public ConnectionTestResult testConnection() {
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(5);
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ConnectionTestResult.builder()
                .success(isValid)
                .responseTimeMs(responseTime)
                .build();
                
        } catch (SQLException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ConnectionTestResult.builder()
                .success(false)
                .responseTimeMs(responseTime)
                .errorMessage("Connection failed: " + e.getMessage())
                .build();
        }
    }
}