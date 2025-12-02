package com.waqiti.common.database;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database Performance Monitor
 * 
 * Monitors:
 * - Query execution times
 * - Connection acquisition times
 * - Database health and availability
 * - Long-running queries
 * - Connection pool metrics
 * - Database locks and blocking
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "database.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class DatabasePerformanceMonitor implements HealthIndicator {

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;

    @Value("${database.monitoring.slow-query-threshold:5000}")
    private long slowQueryThresholdMs;

    @Value("${database.monitoring.connection-timeout-threshold:10000}")
    private long connectionTimeoutThresholdMs;

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;

    // Metrics
    private final Timer connectionAcquisitionTimer;
    private final Timer queryExecutionTimer;
    private final Counter slowQueryCounter;
    private final Counter connectionTimeoutCounter;
    private final AtomicLong lastHealthCheckTime = new AtomicLong(System.currentTimeMillis());
    private volatile boolean lastHealthCheckResult = true;

    public DatabasePerformanceMonitor(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.connectionAcquisitionTimer = Timer.builder("db.connection.acquisition")
                .description("Time taken to acquire database connection")
                .tag("service", serviceName)
                .register(meterRegistry);
                
        this.queryExecutionTimer = Timer.builder("db.query.execution")
                .description("Database query execution time")
                .tag("service", serviceName)
                .register(meterRegistry);
                
        this.slowQueryCounter = Counter.builder("db.query.slow")
                .description("Number of slow database queries")
                .tag("service", serviceName)
                .register(meterRegistry);
                
        this.connectionTimeoutCounter = Counter.builder("db.connection.timeout")
                .description("Number of database connection timeouts")
                .tag("service", serviceName)
                .register(meterRegistry);
                
        // Register gauges for real-time metrics
        registerGauges();
        
        log.info("Database performance monitoring initialized for service: {}", serviceName);
    }

    /**
     * Scheduled health check and metrics collection
     */
    @Scheduled(fixedRateString = "${database.monitoring.interval:30000}")
    public void performHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Test connection acquisition time
            Timer.Sample connectionSample = Timer.start(meterRegistry);
            
            try (Connection connection = dataSource.getConnection()) {
                connectionSample.stop(connectionAcquisitionTimer);
                
                long connectionTime = System.currentTimeMillis() - startTime;
                if (connectionTime > connectionTimeoutThresholdMs) {
                    connectionTimeoutCounter.increment();
                    log.warn("Slow connection acquisition: {}ms (threshold: {}ms)", 
                            connectionTime, connectionTimeoutThresholdMs);
                }
                
                // Test query execution
                testQueryExecution(connection);
                
                // Collect database metrics
                collectDatabaseMetrics(connection);
                
                lastHealthCheckResult = true;
                
            }
        } catch (SQLException e) {
            lastHealthCheckResult = false;
            log.error("Database health check failed", e);
            
            // Increment error metrics
            Counter.builder("db.health.check.failure")
                    .description("Database health check failures")
                    .tag("service", serviceName)
                    .tag("error", e.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
        }
        
        lastHealthCheckTime.set(System.currentTimeMillis());
    }

    /**
     * Test basic query execution performance
     */
    private void testQueryExecution(Connection connection) throws SQLException {
        Timer.Sample querySample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try (PreparedStatement stmt = connection.prepareStatement("SELECT 1 as health_check")) {
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                
                long executionTime = System.currentTimeMillis() - startTime;
                querySample.stop(Timer.builder("db.health.check.query")
                        .description("Health check query execution time")
                        .tag("service", serviceName)
                        .register(meterRegistry));
                
                if (executionTime > slowQueryThresholdMs) {
                    slowQueryCounter.increment();
                    log.warn("Slow health check query: {}ms (threshold: {}ms)", 
                            executionTime, slowQueryThresholdMs);
                }
            }
        }
    }

    /**
     * Collect database-specific metrics
     */
    private void collectDatabaseMetrics(Connection connection) {
        try {
            // PostgreSQL-specific metrics
            if (connection.getMetaData().getDatabaseProductName().contains("PostgreSQL")) {
                collectPostgreSQLMetrics(connection);
            }
        } catch (SQLException e) {
            log.debug("Could not collect database-specific metrics", e);
        }
    }

    /**
     * Collect PostgreSQL-specific performance metrics
     */
    private void collectPostgreSQLMetrics(Connection connection) {
        try {
            // Active connections
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT count(*) as active_connections FROM pg_stat_activity WHERE state = 'active'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int activeConnections = rs.getInt("active_connections");
                        meterRegistry.gauge("db.connections.active", 
                                Tags.of("service", serviceName), 
                                activeConnections);
                    }
                }
            }
            
            // Long-running queries
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT count(*) as long_queries FROM pg_stat_activity " +
                    "WHERE state = 'active' AND now() - query_start > interval '30 seconds'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int longQueries = rs.getInt("long_queries");
                        if (longQueries > 0) {
                            log.warn("Found {} long-running queries (>30s)", longQueries);
                            Counter.builder("db.queries.long_running")
                                    .description("Number of long-running queries")
                                    .tag("service", serviceName)
                                    .register(meterRegistry)
                                    .increment(longQueries);
                        }
                    }
                }
            }
            
            // Blocked queries
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT count(*) as blocked_queries FROM pg_stat_activity WHERE wait_event_type = 'Lock'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int blockedQueries = rs.getInt("blocked_queries");
                        if (blockedQueries > 0) {
                            log.warn("Found {} blocked queries", blockedQueries);
                            Counter.builder("db.queries.blocked")
                                    .description("Number of blocked queries")
                                    .tag("service", serviceName)
                                    .register(meterRegistry)
                                    .increment(blockedQueries);
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            log.debug("Could not collect PostgreSQL metrics", e);
        }
    }

    /**
     * Register gauge metrics for real-time monitoring
     */
    private void registerGauges() {
        // Database response time gauge
        Gauge.builder("db.response.time", this, monitor -> {
                    try (Connection conn = dataSource.getConnection()) {
                        long start = System.nanoTime();
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                            stmt.executeQuery();
                        }
                        return (double) Duration.ofNanos(System.nanoTime() - start).toMillis();
                    } catch (SQLException e) {
                        return -1.0; // Error indicator
                    }
                })
                .description("Database response time in milliseconds")
                .tag("service", serviceName)
                .register(meterRegistry);

        // Last health check timestamp
        Gauge.builder("db.last.health.check", lastHealthCheckTime, AtomicLong::doubleValue)
                .description("Timestamp of last successful health check")
                .tag("service", serviceName)
                .register(meterRegistry);
    }

    /**
     * Spring Boot Health Indicator implementation
     */
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            // Test connection validity
            if (!connection.isValid(5)) {
                return Health.down()
                        .withDetail("reason", "Connection validation failed")
                        .withDetail("service", serviceName)
                        .build();
            }
            
            // Test basic query
            long startTime = System.currentTimeMillis();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                }
            }
            long responseTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> details = new HashMap<>();
            details.put("service", serviceName);
            details.put("responseTime", responseTime + "ms");
            details.put("lastCheck", LocalDateTime.now().toString());
            details.put("database", connection.getMetaData().getDatabaseProductName());
            details.put("driver", connection.getMetaData().getDriverName());
            
            // Add performance indicators
            if (responseTime > slowQueryThresholdMs) {
                details.put("warning", "High response time detected");
                return Health.up().withDetails(details).build();
            }
            
            return Health.up().withDetails(details).build();
            
        } catch (SQLException e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("service", serviceName)
                    .withException(e)
                    .build();
        }
    }

    /**
     * Get current performance statistics
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("service", serviceName);
        stats.put("connectionAcquisitionCount", connectionAcquisitionTimer.count());
        stats.put("connectionAcquisitionMean", connectionAcquisitionTimer.mean(TimeUnit.MILLISECONDS));
        stats.put("queryExecutionCount", queryExecutionTimer.count());
        stats.put("queryExecutionMean", queryExecutionTimer.mean(TimeUnit.MILLISECONDS));
        stats.put("slowQueryCount", slowQueryCounter.count());
        stats.put("connectionTimeoutCount", connectionTimeoutCounter.count());
        stats.put("lastHealthCheck", lastHealthCheckTime.get());
        stats.put("healthStatus", lastHealthCheckResult ? "UP" : "DOWN");
        
        return stats;
    }
}