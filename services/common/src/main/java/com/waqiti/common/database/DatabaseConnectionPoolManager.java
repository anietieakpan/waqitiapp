package com.waqiti.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Database Connection Pool Manager
 * 
 * Provides sophisticated connection pool management, monitoring, and optimization
 * for high-volume financial applications.
 */
@Component
@Slf4j
public class DatabaseConnectionPoolManager implements HealthIndicator {

    private final HikariDataSource primaryDataSource;
    private final HikariDataSource readReplicaDataSource;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler;
    
    // Configuration properties
    @Value("${database.pool.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    @Value("${database.pool.warning-threshold.active-connections-percentage:80}")
    private int warningThreshold;
    
    @Value("${database.pool.critical-threshold.active-connections-percentage:95}")
    private int criticalThreshold;
    
    @Value("${database.pool.slow-query-threshold:10000}")
    private long slowQueryThreshold;
    
    // Metrics
    private final AtomicLong connectionLeakCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    private final AtomicLong poolExhaustionCount = new AtomicLong(0);
    
    public DatabaseConnectionPoolManager(
            HikariDataSource primaryDataSource,
            HikariDataSource readReplicaDataSource,
            MeterRegistry meterRegistry) {
        
        this.primaryDataSource = primaryDataSource;
        this.readReplicaDataSource = readReplicaDataSource;
        this.meterRegistry = meterRegistry;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        initializeMonitoring();
        scheduleMaintenanceTasks();
    }

    /**
     * Initialize connection pool monitoring and metrics
     */
    private void initializeMonitoring() {
        if (!monitoringEnabled) {
            return;
        }
        
        log.info("Initializing database connection pool monitoring");
        
        // Register custom metrics
        meterRegistry.gauge("database.pool.primary.active", primaryDataSource, ds -> ds.getHikariPoolMXBean().getActiveConnections());
        meterRegistry.gauge("database.pool.primary.idle", primaryDataSource, ds -> ds.getHikariPoolMXBean().getIdleConnections());
        meterRegistry.gauge("database.pool.primary.total", primaryDataSource, ds -> ds.getHikariPoolMXBean().getTotalConnections());
        meterRegistry.gauge("database.pool.primary.waiting", primaryDataSource, ds -> ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
        
        if (readReplicaDataSource != null) {
            meterRegistry.gauge("database.pool.replica.active", readReplicaDataSource, ds -> ds.getHikariPoolMXBean().getActiveConnections());
            meterRegistry.gauge("database.pool.replica.idle", readReplicaDataSource, ds -> ds.getHikariPoolMXBean().getIdleConnections());
            meterRegistry.gauge("database.pool.replica.total", readReplicaDataSource, ds -> ds.getHikariPoolMXBean().getTotalConnections());
            meterRegistry.gauge("database.pool.replica.waiting", readReplicaDataSource, ds -> ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
        }
        
        // Register counters for issues
        meterRegistry.gauge("database.pool.connection.leaks", connectionLeakCount);
        meterRegistry.gauge("database.pool.slow.queries", slowQueryCount);
        meterRegistry.gauge("database.pool.exhaustion.events", poolExhaustionCount);
        
        log.info("Database connection pool monitoring initialized successfully");
    }

    /**
     * Schedule maintenance and monitoring tasks
     */
    private void scheduleMaintenanceTasks() {
        // Pool health monitoring every 30 seconds
        scheduler.scheduleAtFixedRate(this::monitorPoolHealth, 30, 30, TimeUnit.SECONDS);
        
        // Detailed pool statistics every 5 minutes
        scheduler.scheduleAtFixedRate(this::logPoolStatistics, 300, 300, TimeUnit.SECONDS);
        
        // Connection leak detection every 2 minutes
        scheduler.scheduleAtFixedRate(this::detectConnectionLeaks, 120, 120, TimeUnit.SECONDS);
    }

    /**
     * Monitor connection pool health and alert on issues
     */
    private void monitorPoolHealth() {
        try {
            monitorDataSourceHealth("primary", primaryDataSource);
            
            if (readReplicaDataSource != null) {
                monitorDataSourceHealth("replica", readReplicaDataSource);
            }
        } catch (Exception e) {
            log.error("Error monitoring pool health", e);
        }
    }

    /**
     * Monitor individual data source health
     */
    private void monitorDataSourceHealth(String poolName, HikariDataSource dataSource) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        
        int activeConnections = poolMXBean.getActiveConnections();
        int totalConnections = poolMXBean.getTotalConnections();
        int waitingThreads = poolMXBean.getThreadsAwaitingConnection();
        
        // Calculate utilization percentage
        double utilizationPercentage = totalConnections > 0 ? 
            (double) activeConnections / totalConnections * 100 : 0;
        
        // Check thresholds
        if (utilizationPercentage >= criticalThreshold) {
            log.error(String.format("CRITICAL: %s pool utilization at %.1f%% - Active: %d, Total: %d, Waiting: %d", 
                poolName, utilizationPercentage, activeConnections, totalConnections, waitingThreads));
            
            if (waitingThreads > 0) {
                poolExhaustionCount.incrementAndGet();
                log.error("Pool exhaustion detected - {} threads waiting for connections", waitingThreads);
            }
        } else if (utilizationPercentage >= warningThreshold) {
            log.warn(String.format("WARNING: %s pool utilization at %.1f%% - Active: %d, Total: %d, Waiting: %d", 
                poolName, utilizationPercentage, activeConnections, totalConnections, waitingThreads));
        }
        
        // Record metrics
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("database.pool.health.check")
            .tag("pool", poolName)
            .register(meterRegistry));
    }

    /**
     * Log detailed pool statistics
     */
    private void logPoolStatistics() {
        try {
            logDataSourceStatistics("primary", primaryDataSource);
            
            if (readReplicaDataSource != null) {
                logDataSourceStatistics("replica", readReplicaDataSource);
            }
        } catch (Exception e) {
            log.error("Error logging pool statistics", e);
        }
    }

    /**
     * Log statistics for individual data source
     */
    private void logDataSourceStatistics(String poolName, HikariDataSource dataSource) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        
        log.info("Pool Statistics [{}]: Active={}, Idle={}, Total={}, Waiting={}", 
            poolName,
            poolMXBean.getActiveConnections(),
            poolMXBean.getIdleConnections(), 
            poolMXBean.getTotalConnections(),
            poolMXBean.getThreadsAwaitingConnection()
        );
    }

    /**
     * Detect and report connection leaks
     */
    private void detectConnectionLeaks() {
        try {
            detectLeaksInDataSource("primary", primaryDataSource);
            
            if (readReplicaDataSource != null) {
                detectLeaksInDataSource("replica", readReplicaDataSource);
            }
        } catch (Exception e) {
            log.error("Error detecting connection leaks", e);
        }
    }

    /**
     * Detect leaks in individual data source
     */
    private void detectLeaksInDataSource(String poolName, HikariDataSource dataSource) {
        // HikariCP automatically detects connection leaks based on configuration
        // This method provides additional monitoring
        
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        int activeConnections = poolMXBean.getActiveConnections();
        int maxPoolSize = dataSource.getMaximumPoolSize();
        
        // If all connections are active for an extended period, investigate
        if (activeConnections == maxPoolSize) {
            log.warn("All connections in {} pool are active - potential leak investigation needed", poolName);
            connectionLeakCount.incrementAndGet();
        }
        
        // Check if leak detection threshold is configured (from dataSource properties)
        long leakDetectionThreshold = dataSource.getLeakDetectionThreshold();
        if (leakDetectionThreshold > 0) {
            log.debug("Leak detection enabled for {} pool with threshold: {}ms", poolName, leakDetectionThreshold);
        }
    }

    /**
     * Get a connection from the primary pool with monitoring
     */
    public Connection getPrimaryConnection() throws SQLException {
        return getConnectionWithMonitoring(primaryDataSource, "primary");
    }

    /**
     * Get a connection from the read replica pool with monitoring
     */
    public Connection getReadReplicaConnection() throws SQLException {
        if (readReplicaDataSource == null) {
            throw new SQLException("Read replica data source not configured");
        }
        return getConnectionWithMonitoring(readReplicaDataSource, "replica");
    }

    /**
     * Get connection with timing and monitoring
     */
    private Connection getConnectionWithMonitoring(HikariDataSource dataSource, String poolName) throws SQLException {
        Instant start = Instant.now();
        
        try {
            Connection connection = dataSource.getConnection();
            
            Duration duration = Duration.between(start, Instant.now());
            long durationMs = duration.toMillis();
            
            // Record timing metrics
            Timer.builder("database.connection.acquisition.time")
                .tag("pool", poolName)
                .register(meterRegistry)
                .record(duration);
            
            // Warn on slow connection acquisition
            if (durationMs > 5000) { // 5 seconds
                log.warn("Slow connection acquisition from {} pool: {}ms", poolName, durationMs);
            }
            
            return connection;
            
        } catch (SQLException e) {
            log.error("Failed to acquire connection from {} pool: {}", poolName, e.getMessage());
            
            // Record failure metrics
            meterRegistry.counter("database.connection.acquisition.failures", "pool", poolName).increment();
            
            throw e;
        }
    }

    /**
     * Execute a query with performance monitoring
     */
    public <T> T executeWithMonitoring(String queryType, QueryExecutor<T> executor) throws SQLException {
        Instant start = Instant.now();
        
        try {
            T result = executor.execute();
            
            Duration duration = Duration.between(start, Instant.now());
            long durationMs = duration.toMillis();
            
            // Record query timing
            Timer.builder("database.query.execution.time")
                .tag("type", queryType)
                .register(meterRegistry)
                .record(duration);
            
            // Check for slow queries
            if (durationMs > slowQueryThreshold) {
                log.warn("Slow query detected [{}]: {}ms", queryType, durationMs);
                slowQueryCount.incrementAndGet();
            }
            
            return result;
            
        } catch (SQLException e) {
            log.error("Query execution failed [{}]: {}", queryType, e.getMessage());
            
            meterRegistry.counter("database.query.failures", "type", queryType).increment();
            throw e;
        }
    }

    /**
     * Functional interface for query execution
     */
    @FunctionalInterface
    public interface QueryExecutor<T> {
        T execute() throws SQLException;
    }

    /**
     * Health check implementation
     */
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        try {
            // Check primary data source
            checkDataSourceHealth(builder, "primary", primaryDataSource);
            
            // Check read replica if configured
            if (readReplicaDataSource != null) {
                checkDataSourceHealth(builder, "replica", readReplicaDataSource);
            }
            
            // Add overall statistics
            builder.withDetail("connection.leaks", connectionLeakCount.get())
                   .withDetail("slow.queries", slowQueryCount.get())
                   .withDetail("pool.exhaustion.events", poolExhaustionCount.get());
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            builder.down().withException(e);
        }
        
        return builder.build();
    }

    /**
     * Check individual data source health
     */
    private void checkDataSourceHealth(Health.Builder builder, String poolName, HikariDataSource dataSource) {
        try {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            
            int activeConnections = poolMXBean.getActiveConnections();
            int totalConnections = poolMXBean.getTotalConnections();
            int waitingThreads = poolMXBean.getThreadsAwaitingConnection();
            
            double utilizationPercentage = totalConnections > 0 ? 
                (double) activeConnections / totalConnections * 100 : 0;
            
            builder.withDetail(poolName + ".active", activeConnections)
                   .withDetail(poolName + ".total", totalConnections)
                   .withDetail(poolName + ".waiting", waitingThreads)
                   .withDetail(poolName + ".utilization", String.format("%.1f%%", utilizationPercentage));
            
            // Test connection
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    throw new SQLException("Connection validation failed");
                }
            }
            
            builder.withDetail(poolName + ".status", "UP");
            
        } catch (Exception e) {
            log.error("Health check failed for {} pool", poolName, e);
            builder.withDetail(poolName + ".status", "DOWN")
                   .withDetail(poolName + ".error", e.getMessage());
        }
    }

    /**
     * Graceful shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down database connection pool manager");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("Database connection pool manager shutdown completed");
    }

    /**
     * Get current pool statistics for monitoring
     */
    public PoolStatistics getPrimaryPoolStatistics() {
        return getPoolStatistics(primaryDataSource);
    }

    public PoolStatistics getReplicaPoolStatistics() {
        return readReplicaDataSource != null ? getPoolStatistics(readReplicaDataSource) : null;
    }

    private PoolStatistics getPoolStatistics(HikariDataSource dataSource) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        
        return PoolStatistics.builder()
            .activeConnections(poolMXBean.getActiveConnections())
            .idleConnections(poolMXBean.getIdleConnections())
            .totalConnections(poolMXBean.getTotalConnections())
            .threadsAwaitingConnection(poolMXBean.getThreadsAwaitingConnection())
            .utilizationPercentage(poolMXBean.getTotalConnections() > 0 ? 
                (double) poolMXBean.getActiveConnections() / poolMXBean.getTotalConnections() * 100 : 0)
            .build();
    }

    /**
     * Pool statistics data class
     */
    public static class PoolStatistics {
        private final int activeConnections;
        private final int idleConnections;
        private final int totalConnections;
        private final int threadsAwaitingConnection;
        private final double utilizationPercentage;

        private PoolStatistics(Builder builder) {
            this.activeConnections = builder.activeConnections;
            this.idleConnections = builder.idleConnections;
            this.totalConnections = builder.totalConnections;
            this.threadsAwaitingConnection = builder.threadsAwaitingConnection;
            this.utilizationPercentage = builder.utilizationPercentage;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getTotalConnections() { return totalConnections; }
        public int getThreadsAwaitingConnection() { return threadsAwaitingConnection; }
        public double getUtilizationPercentage() { return utilizationPercentage; }

        public static class Builder {
            private int activeConnections;
            private int idleConnections;
            private int totalConnections;
            private int threadsAwaitingConnection;
            private double utilizationPercentage;

            public Builder activeConnections(int activeConnections) {
                this.activeConnections = activeConnections;
                return this;
            }

            public Builder idleConnections(int idleConnections) {
                this.idleConnections = idleConnections;
                return this;
            }

            public Builder totalConnections(int totalConnections) {
                this.totalConnections = totalConnections;
                return this;
            }

            public Builder threadsAwaitingConnection(int threadsAwaitingConnection) {
                this.threadsAwaitingConnection = threadsAwaitingConnection;
                return this;
            }

            public Builder utilizationPercentage(double utilizationPercentage) {
                this.utilizationPercentage = utilizationPercentage;
                return this;
            }

            public PoolStatistics build() {
                return new PoolStatistics(this);
            }
        }
    }
}