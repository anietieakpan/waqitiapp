package com.waqiti.common.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database Connection Pool Monitoring
 * Provides comprehensive monitoring and health checks for database connection pools
 * 
 * Features:
 * - HikariCP connection pool metrics
 * - Connection leak detection
 * - Pool health monitoring
 * - Connection acquisition time tracking
 * - Alerts for pool exhaustion
 * - Connection usage analytics
 */
@Component
@ConditionalOnClass(HikariDataSource.class)
@RequiredArgsConstructor
@Slf4j
public class DatabaseConnectionPoolMonitoring implements HealthIndicator {

    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;

    private final Map<String, HikariDataSource> monitoredDataSources = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPoolMetrics> poolMetrics = new ConcurrentHashMap<>();
    
    // Alert thresholds
    private static final double HIGH_USAGE_THRESHOLD = 0.8; // 80%
    private static final double CRITICAL_USAGE_THRESHOLD = 0.95; // 95%
    private static final long CONNECTION_LEAK_THRESHOLD_MS = 300000; // 5 minutes
    private static final int MAX_CONNECTION_ACQUISITION_TIME_MS = 30000; // 30 seconds

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMonitoring() {
        log.info("Initializing database connection pool monitoring");
        
        // Discover all HikariCP data sources
        Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);
        
        dataSources.forEach((beanName, dataSource) -> {
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                monitoredDataSources.put(beanName, hikariDataSource);
                setupMetrics(beanName, hikariDataSource);
                log.info("Started monitoring connection pool: {}", beanName);
            }
        });
        
        log.info("Database connection pool monitoring initialized for {} pools", monitoredDataSources.size());
    }

    /**
     * Setup metrics for a HikariCP data source
     */
    private void setupMetrics(String poolName, HikariDataSource dataSource) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        ConnectionPoolMetrics metrics = new ConnectionPoolMetrics();
        poolMetrics.put(poolName, metrics);

        // Connection pool size metrics
        Gauge.builder("waqiti.db.pool.size.total", poolMXBean, HikariPoolMXBean::getTotalConnections)
            .description("Total connections in the pool")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        Gauge.builder("waqiti.db.pool.size.active", poolMXBean, HikariPoolMXBean::getActiveConnections)
            .description("Active connections in the pool")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        Gauge.builder("waqiti.db.pool.size.idle", poolMXBean, HikariPoolMXBean::getIdleConnections)
            .description("Idle connections in the pool")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        // Connection usage metrics
        Gauge.builder("waqiti.db.pool.usage.ratio", this, self -> self.calculateUsageRatio(poolName))
            .description("Connection pool usage ratio")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        // Connection wait metrics
        Gauge.builder("waqiti.db.pool.threads.waiting", poolMXBean, HikariPoolMXBean::getThreadsAwaitingConnection)
            .description("Threads waiting for connections")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        // Connection acquisition time
        Gauge.builder("waqiti.db.pool.connection.acquisition.time.avg", this, self -> self.getAverageConnectionAcquisitionTime(poolName))
            .description("Average connection acquisition time")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        // Connection leak detection
        Gauge.builder("waqiti.db.pool.leaks.detected", metrics, ConnectionPoolMetrics::getLeakCount)
            .description("Number of connection leaks detected")
            .tags(Tags.of("pool", poolName))
            .register(meterRegistry);

        log.debug("Metrics setup completed for pool: {}", poolName);
    }

    /**
     * Calculate connection pool usage ratio
     */
    private double calculateUsageRatio(String poolName) {
        HikariDataSource dataSource = monitoredDataSources.get(poolName);
        if (dataSource == null || dataSource.getHikariPoolMXBean() == null) {
            return 0.0;
        }

        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        int totalConnections = poolMXBean.getTotalConnections();
        int activeConnections = poolMXBean.getActiveConnections();

        if (totalConnections == 0) {
            return 0.0;
        }

        return (double) activeConnections / totalConnections;
    }

    /**
     * Get average connection acquisition time
     */
    private double getAverageConnectionAcquisitionTime(String poolName) {
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
        return metrics != null ? metrics.getAverageAcquisitionTime() : 0.0;
    }

    /**
     * Monitor connection pool health periodically
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorPoolHealth() {
        monitoredDataSources.forEach((poolName, dataSource) -> {
            try {
                monitorSinglePool(poolName, dataSource);
            } catch (Exception e) {
                log.error("Error monitoring pool: {}", poolName, e);
            }
        });
    }

    /**
     * Monitor a single connection pool
     */
    private void monitorSinglePool(String poolName, HikariDataSource dataSource) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);

        if (poolMXBean == null || metrics == null) {
            return;
        }

        // Check pool usage
        double usageRatio = calculateUsageRatio(poolName);
        metrics.updateUsageRatio(usageRatio);

        // Alert on high usage
        if (usageRatio >= CRITICAL_USAGE_THRESHOLD) {
            log.error("CRITICAL: Connection pool {} usage at {:.1f}%", poolName, usageRatio * 100);
            metrics.incrementCriticalAlerts();
        } else if (usageRatio >= HIGH_USAGE_THRESHOLD) {
            log.warn("WARNING: Connection pool {} usage at {:.1f}%", poolName, usageRatio * 100);
            metrics.incrementWarningAlerts();
        }

        // Check for threads waiting for connections
        int threadsWaiting = poolMXBean.getThreadsAwaitingConnection();
        if (threadsWaiting > 0) {
            log.warn("Pool {} has {} threads waiting for connections", poolName, threadsWaiting);
            metrics.incrementThreadsWaitingEvents();
        }

        // Test connection acquisition time
        testConnectionAcquisitionTime(poolName, dataSource, metrics);

        // Update metrics
        metrics.updateLastMonitorTime();
    }

    /**
     * Test connection acquisition time
     */
    private void testConnectionAcquisitionTime(String poolName, HikariDataSource dataSource, ConnectionPoolMetrics metrics) {
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            long acquisitionTime = System.currentTimeMillis() - startTime;
            metrics.updateConnectionAcquisitionTime(acquisitionTime);
            
            if (acquisitionTime > MAX_CONNECTION_ACQUISITION_TIME_MS) {
                log.warn("Slow connection acquisition for pool {}: {}ms", poolName, acquisitionTime);
                metrics.incrementSlowAcquisitionCount();
            }
            
            // Test connection validity
            if (!connection.isValid(5)) {
                log.error("Invalid connection detected in pool: {}", poolName);
                metrics.incrementInvalidConnectionCount();
            }
            
        } catch (SQLException e) {
            log.error("Connection acquisition test failed for pool: {}", poolName, e);
            metrics.incrementConnectionErrors();
        }
    }

    /**
     * Detect connection leaks
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void detectConnectionLeaks() {
        monitoredDataSources.forEach((poolName, dataSource) -> {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
            
            if (poolMXBean == null || metrics == null) {
                return;
            }

            int activeConnections = poolMXBean.getActiveConnections();
            long currentTime = System.currentTimeMillis();
            
            // Simple leak detection: if connections have been active for too long
            if (activeConnections > 0) {
                long lastLeakCheck = metrics.getLastLeakCheckTime();
                if (lastLeakCheck > 0 && (currentTime - lastLeakCheck) > CONNECTION_LEAK_THRESHOLD_MS) {
                    // If same connections are still active after threshold time, potential leak
                    int previousActiveConnections = metrics.getPreviousActiveConnections();
                    if (activeConnections >= previousActiveConnections && activeConnections > dataSource.getMaximumPoolSize() / 2) {
                        log.warn("Potential connection leak detected in pool: {} - {} connections active for over {} minutes", 
                            poolName, activeConnections, CONNECTION_LEAK_THRESHOLD_MS / 60000);
                        metrics.incrementLeakCount();
                    }
                }
                
                metrics.setPreviousActiveConnections(activeConnections);
            }
            
            metrics.setLastLeakCheckTime(currentTime);
        });
    }

    /**
     * Health check implementation
     */
    @Override
    public Health health() {
        Health.Builder healthBuilder = Health.up();
        boolean hasUnhealthyPool = false;
        
        for (Map.Entry<String, HikariDataSource> entry : monitoredDataSources.entrySet()) {
            String poolName = entry.getKey();
            HikariDataSource dataSource = entry.getValue();
            ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
            
            Health.Builder poolHealthBuilder = Health.up();
            
            try {
                HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
                
                if (poolMXBean == null) {
                    poolHealthBuilder.down().withDetail("reason", "Pool MXBean not available");
                    hasUnhealthyPool = true;
                    continue;
                }

                // Check pool usage
                double usageRatio = calculateUsageRatio(poolName);
                poolHealthBuilder.withDetail("usage_ratio", String.format("%.2f%%", usageRatio * 100));
                
                if (usageRatio >= CRITICAL_USAGE_THRESHOLD) {
                    poolHealthBuilder.down().withDetail("reason", "Critical pool usage");
                    hasUnhealthyPool = true;
                } else if (usageRatio >= HIGH_USAGE_THRESHOLD) {
                    poolHealthBuilder.status("WARNING").withDetail("reason", "High pool usage");
                }
                
                // Add pool details
                poolHealthBuilder
                    .withDetail("total_connections", poolMXBean.getTotalConnections())
                    .withDetail("active_connections", poolMXBean.getActiveConnections())
                    .withDetail("idle_connections", poolMXBean.getIdleConnections())
                    .withDetail("threads_waiting", poolMXBean.getThreadsAwaitingConnection())
                    .withDetail("max_pool_size", dataSource.getMaximumPoolSize())
                    .withDetail("min_idle", dataSource.getMinimumIdle());
                
                // Add metrics if available
                if (metrics != null) {
                    poolHealthBuilder
                        .withDetail("average_acquisition_time_ms", metrics.getAverageAcquisitionTime())
                        .withDetail("connection_errors", metrics.getConnectionErrors())
                        .withDetail("potential_leaks", metrics.getLeakCount())
                        .withDetail("critical_alerts", metrics.getCriticalAlerts())
                        .withDetail("warning_alerts", metrics.getWarningAlerts());
                }
                
            } catch (Exception e) {
                poolHealthBuilder.down()
                    .withDetail("reason", "Error checking pool health")
                    .withDetail("error", e.getMessage());
                hasUnhealthyPool = true;
            }
            
            healthBuilder.withDetail("pool_" + poolName, poolHealthBuilder.build());
        }
        
        if (hasUnhealthyPool) {
            healthBuilder.down();
        }
        
        healthBuilder
            .withDetail("monitored_pools", monitoredDataSources.size())
            .withDetail("monitoring_active", true);
        
        return healthBuilder.build();
    }

    /**
     * Connection pool metrics holder
     */
    private static class ConnectionPoolMetrics {
        private final AtomicLong connectionErrors = new AtomicLong(0);
        private final AtomicLong leakCount = new AtomicLong(0);
        private final AtomicLong criticalAlerts = new AtomicLong(0);
        private final AtomicLong warningAlerts = new AtomicLong(0);
        private final AtomicLong threadsWaitingEvents = new AtomicLong(0);
        private final AtomicLong slowAcquisitionCount = new AtomicLong(0);
        private final AtomicLong invalidConnectionCount = new AtomicLong(0);
        
        private volatile double currentUsageRatio = 0.0;
        private volatile double averageAcquisitionTime = 0.0;
        private volatile long lastMonitorTime = 0L;
        private volatile long lastLeakCheckTime = 0L;
        private volatile int previousActiveConnections = 0;
        
        // Connection acquisition time tracking
        private final AtomicLong totalAcquisitionTime = new AtomicLong(0);
        private final AtomicLong acquisitionCount = new AtomicLong(0);

        public void updateUsageRatio(double ratio) {
            this.currentUsageRatio = ratio;
        }

        public void updateConnectionAcquisitionTime(long acquisitionTimeMs) {
            totalAcquisitionTime.addAndGet(acquisitionTimeMs);
            long count = acquisitionCount.incrementAndGet();
            averageAcquisitionTime = (double) totalAcquisitionTime.get() / count;
        }

        public void updateLastMonitorTime() {
            lastMonitorTime = System.currentTimeMillis();
        }

        // Increment methods
        public void incrementConnectionErrors() { connectionErrors.incrementAndGet(); }
        public void incrementLeakCount() { leakCount.incrementAndGet(); }
        public void incrementCriticalAlerts() { criticalAlerts.incrementAndGet(); }
        public void incrementWarningAlerts() { warningAlerts.incrementAndGet(); }
        public void incrementThreadsWaitingEvents() { threadsWaitingEvents.incrementAndGet(); }
        public void incrementSlowAcquisitionCount() { slowAcquisitionCount.incrementAndGet(); }
        public void incrementInvalidConnectionCount() { invalidConnectionCount.incrementAndGet(); }

        // Getters
        public long getConnectionErrors() { return connectionErrors.get(); }
        public long getLeakCount() { return leakCount.get(); }
        public long getCriticalAlerts() { return criticalAlerts.get(); }
        public long getWarningAlerts() { return warningAlerts.get(); }
        public double getAverageAcquisitionTime() { return averageAcquisitionTime; }
        public long getLastLeakCheckTime() { return lastLeakCheckTime; }
        public int getPreviousActiveConnections() { return previousActiveConnections; }

        public void setLastLeakCheckTime(long time) { lastLeakCheckTime = time; }
        public void setPreviousActiveConnections(int count) { previousActiveConnections = count; }
    }
}