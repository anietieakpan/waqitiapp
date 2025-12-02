package com.waqiti.common.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced connection pool optimizer with dynamic sizing and performance tuning
 */
@Configuration
@Slf4j
public class ConnectionPoolOptimizer {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource optimizedDataSource(DataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        
        // Basic configuration
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        
        // Optimized pool sizing based on formula: connections = ((core_count * 2) + effective_spindle_count)
        int coreCount = Runtime.getRuntime().availableProcessors();
        int optimalPoolSize = (coreCount * 2) + 1; // +1 for effective spindle count on SSD
        
        // Pool size configuration
        config.setMinimumIdle(Math.max(5, optimalPoolSize / 2));
        config.setMaximumPoolSize(Math.min(optimalPoolSize * 2, 50)); // Cap at 50
        
        // Connection timeout settings
        config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
        config.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        config.setKeepaliveTime(Duration.ofMinutes(5).toMillis());
        
        // Validation settings
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(Duration.ofSeconds(5).toMillis());
        
        // Performance optimizations
        config.setAutoCommit(false); // Explicit transaction management
        config.setReadOnly(false);
        config.setIsolateInternalQueries(false);
        config.setRegisterMbeans(true); // Enable JMX monitoring
        config.setAllowPoolSuspension(false);
        
        // Pool name for identification
        config.setPoolName("WaqitiOptimizedPool");
        
        // Leak detection
        config.setLeakDetectionThreshold(Duration.ofSeconds(60).toMillis());
        
        // Connection init SQL
        config.setConnectionInitSql("SET TIME ZONE 'UTC'");
        
        // Advanced properties
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("netTimeoutForStreamingResults", "0");
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        log.info("Optimized connection pool created with size: {}-{}, optimal size calculated: {}", 
            config.getMinimumIdle(), config.getMaximumPoolSize(), optimalPoolSize);
            
        return dataSource;
    }
    
    /**
     * Dynamic pool size adjuster based on load
     */
    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class DynamicPoolAdjuster {
        
        private final DataSource dataSource;
        private final Map<Long, PoolLoadMetrics> loadHistory = new ConcurrentHashMap<>();
        private final AtomicInteger consecutiveHighLoad = new AtomicInteger(0);
        private final AtomicInteger consecutiveLowLoad = new AtomicInteger(0);
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        private static final int HIGH_LOAD_THRESHOLD = 80; // 80% utilization
        private static final int LOW_LOAD_THRESHOLD = 30;  // 30% utilization
        private static final int ADJUSTMENT_THRESHOLD = 3;  // Consecutive measurements before adjustment
        
        @Scheduled(fixedDelay = 30000, initialDelay = 60000) // Check every 30 seconds
        public void adjustPoolSize() {
            if (!(dataSource instanceof HikariDataSource)) {
                return;
            }
            
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            
            try {
                // Get current metrics
                int activeConnections = hikariDataSource.getHikariPoolMXBean().getActiveConnections();
                int totalConnections = hikariDataSource.getHikariPoolMXBean().getTotalConnections();
                int currentMaxSize = hikariDataSource.getMaximumPoolSize();
                int currentMinIdle = hikariDataSource.getMinimumIdle();
                
                double utilization = totalConnections > 0 
                    ? (double) activeConnections / totalConnections * 100 
                    : 0;
                    
                // Record metrics
                recordLoadMetrics(utilization, activeConnections, totalConnections);
                
                // Determine if adjustment is needed
                if (utilization > HIGH_LOAD_THRESHOLD) {
                    consecutiveHighLoad.incrementAndGet();
                    consecutiveLowLoad.set(0);
                    
                    if (consecutiveHighLoad.get() >= ADJUSTMENT_THRESHOLD) {
                        increasePoolSize(hikariDataSource, currentMaxSize, currentMinIdle);
                        consecutiveHighLoad.set(0);
                    }
                    
                } else if (utilization < LOW_LOAD_THRESHOLD) {
                    consecutiveLowLoad.incrementAndGet();
                    consecutiveHighLoad.set(0);
                    
                    if (consecutiveLowLoad.get() >= ADJUSTMENT_THRESHOLD) {
                        decreasePoolSize(hikariDataSource, currentMaxSize, currentMinIdle);
                        consecutiveLowLoad.set(0);
                    }
                    
                } else {
                    consecutiveHighLoad.set(0);
                    consecutiveLowLoad.set(0);
                }
                
            } catch (Exception e) {
                log.error("Error adjusting pool size", e);
            }
        }
        
        private void increasePoolSize(HikariDataSource dataSource, int currentMax, int currentMin) {
            int newMax = Math.min(currentMax + 5, 100); // Increase by 5, cap at 100
            int newMin = Math.min(currentMin + 2, newMax / 2); // Increase min proportionally
            
            if (newMax > currentMax) {
                dataSource.setMaximumPoolSize(newMax);
                dataSource.setMinimumIdle(newMin);
                log.info("Increased pool size: max {} -> {}, min {} -> {} due to high load", 
                    currentMax, newMax, currentMin, newMin);
            }
        }
        
        private void decreasePoolSize(HikariDataSource dataSource, int currentMax, int currentMin) {
            int coreCount = Runtime.getRuntime().availableProcessors();
            int minAllowedMax = (coreCount * 2) + 1;
            
            int newMax = Math.max(currentMax - 5, minAllowedMax); // Decrease by 5, keep minimum
            int newMin = Math.max(currentMin - 2, 5); // Decrease min, keep at least 5
            
            if (newMax < currentMax) {
                dataSource.setMaximumPoolSize(newMax);
                dataSource.setMinimumIdle(newMin);
                log.info("Decreased pool size: max {} -> {}, min {} -> {} due to low load", 
                    currentMax, newMax, currentMin, newMin);
            }
        }
        
        private void recordLoadMetrics(double utilization, int active, int total) {
            long timestamp = System.currentTimeMillis();
            loadHistory.put(timestamp, PoolLoadMetrics.builder()
                .timestamp(timestamp)
                .utilization(utilization)
                .activeConnections(active)
                .totalConnections(total)
                .build());
                
            // Keep only last hour of metrics
            long oneHourAgo = timestamp - Duration.ofHours(1).toMillis();
            loadHistory.entrySet().removeIf(entry -> entry.getKey() < oneHourAgo);
        }
        
        public PoolOptimizationStats getOptimizationStats() {
            if (!(dataSource instanceof HikariDataSource)) {
                return PoolOptimizationStats.builder()
                    .optimizationEnabled(false)
                    .build();
            }
            
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            
            double avgUtilization = loadHistory.values().stream()
                .mapToDouble(PoolLoadMetrics::getUtilization)
                .average()
                .orElse(0);
                
            return PoolOptimizationStats.builder()
                .optimizationEnabled(true)
                .currentMaxPoolSize(hikariDataSource.getMaximumPoolSize())
                .currentMinIdle(hikariDataSource.getMinimumIdle())
                .averageUtilization(avgUtilization)
                .highLoadCount(consecutiveHighLoad.get())
                .lowLoadCount(consecutiveLowLoad.get())
                .metricsCount(loadHistory.size())
                .build();
        }
    }
    
    /**
     * Connection warmup for optimal performance
     */
    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class ConnectionWarmup {
        
        private final DataSource dataSource;
        private final AtomicLong warmupTime = new AtomicLong(0);
        
        @Scheduled(fixedDelay = 300000, initialDelay = 10000) // Every 5 minutes
        public void warmupConnections() {
            if (!(dataSource instanceof HikariDataSource)) {
                return;
            }
            
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            int minIdle = hikariDataSource.getMinimumIdle();
            
            long startTime = System.currentTimeMillis();
            int warmedUp = 0;
            
            try {
                // Warm up minimum number of connections
                for (int i = 0; i < minIdle; i++) {
                    scheduler.execute(() -> {
                        try (Connection conn = dataSource.getConnection()) {
                            // Validate connection
                            if (conn.isValid(1)) {
                                conn.prepareStatement("SELECT 1").execute();
                            }
                        } catch (SQLException e) {
                            log.debug("Connection warmup failed: {}", e.getMessage());
                        }
                    });
                    warmedUp++;
                }
                
                long duration = System.currentTimeMillis() - startTime;
                warmupTime.set(duration);
                
                log.debug("Warmed up {} connections in {}ms", warmedUp, duration);
                
            } catch (Exception e) {
                log.error("Error during connection warmup", e);
            }
        }
        
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
        
        public long getLastWarmupTime() {
            return warmupTime.get();
        }
    }
    
    /**
     * Connection pool health monitor
     */
    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class PoolHealthMonitor {
        
        private final DataSource dataSource;
        private final Map<String, HealthMetric> healthMetrics = new ConcurrentHashMap<>();
        
        @Scheduled(fixedDelay = 10000, initialDelay = 5000) // Every 10 seconds
        public void monitorHealth() {
            if (!(dataSource instanceof HikariDataSource)) {
                return;
            }
            
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            
            try {
                // Test connection acquisition time
                long startTime = System.currentTimeMillis();
                try (Connection conn = dataSource.getConnection()) {
                    long acquisitionTime = System.currentTimeMillis() - startTime;
                    healthMetrics.put("connectionAcquisitionTime", 
                        HealthMetric.of("connectionAcquisitionTime", acquisitionTime, "ms"));
                        
                    // Test query execution
                    long queryStart = System.currentTimeMillis();
                    conn.prepareStatement("SELECT 1").execute();
                    long queryTime = System.currentTimeMillis() - queryStart;
                    healthMetrics.put("queryExecutionTime", 
                        HealthMetric.of("queryExecutionTime", queryTime, "ms"));
                }
                
                // Get pool metrics
                var poolMXBean = hikariDataSource.getHikariPoolMXBean();
                healthMetrics.put("activeConnections", 
                    HealthMetric.of("activeConnections", poolMXBean.getActiveConnections(), "count"));
                healthMetrics.put("idleConnections", 
                    HealthMetric.of("idleConnections", poolMXBean.getIdleConnections(), "count"));
                healthMetrics.put("threadsAwaitingConnection", 
                    HealthMetric.of("threadsAwaitingConnection", poolMXBean.getThreadsAwaitingConnection(), "count"));
                    
                // Check for issues
                checkHealthIssues();
                
            } catch (Exception e) {
                log.error("Error monitoring pool health", e);
            }
        }
        
        private void checkHealthIssues() {
            Long acquisitionTime = (Long) healthMetrics.get("connectionAcquisitionTime").getValue();
            if (acquisitionTime != null && acquisitionTime > 1000) {
                log.warn("Slow connection acquisition detected: {}ms", acquisitionTime);
            }
            
            Integer threadsWaiting = (Integer) healthMetrics.get("threadsAwaitingConnection").getValue();
            if (threadsWaiting != null && threadsWaiting > 0) {
                log.warn("Threads waiting for connections: {}", threadsWaiting);
            }
        }
        
        public Map<String, HealthMetric> getHealthMetrics() {
            return new ConcurrentHashMap<>(healthMetrics);
        }
    }
    
    // Helper classes
    
    @lombok.Builder
    @lombok.Data
    private static class PoolLoadMetrics {
        private long timestamp;
        private double utilization;
        private int activeConnections;
        private int totalConnections;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class PoolOptimizationStats {
        private boolean optimizationEnabled;
        private int currentMaxPoolSize;
        private int currentMinIdle;
        private double averageUtilization;
        private int highLoadCount;
        private int lowLoadCount;
        private int metricsCount;
    }
    
    @lombok.AllArgsConstructor(staticName = "of")
    @lombok.Data
    public static class HealthMetric {
        private String name;
        private Object value;
        private String unit;
    }
}