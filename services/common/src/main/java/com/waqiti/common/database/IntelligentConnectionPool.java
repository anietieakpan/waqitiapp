package com.waqiti.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intelligent Database Connection Pool
 * 
 * Advanced connection pool management with ML-driven optimization:
 * - Dynamic pool sizing based on predicted load
 * - Connection health monitoring and auto-healing
 * - Query-aware connection routing
 * - Performance-based connection selection
 * - Predictive connection pre-warming
 * - Connection leak detection and prevention
 * - Automatic failover and load balancing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntelligentConnectionPool {

    private final QueryPredictionService queryPredictionService;
    private final DatabaseMetricsCollector metricsCollector;
    private final ConnectionRouter connectionRouter;
    private final ConnectionSelector connectionSelector;
    
    // Multiple data sources for read/write splitting and load balancing
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPoolMetrics> poolMetrics = new ConcurrentHashMap<>();
    private final Map<String, ConnectionHealthStatus> connectionHealth = new ConcurrentHashMap<>();
    
    // Pool configuration
    private volatile ConnectionPoolConfiguration currentConfig;
    private final AtomicLong lastConfigUpdate = new AtomicLong(0);
    
    // Metrics tracking
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong connectionRequestCount = new AtomicLong(0);
    private final AtomicLong connectionWaitTime = new AtomicLong(0);
    
    /**
     * Initializes intelligent connection pool with default configuration
     */
    public void initialize() {
        try {
            log.info("Initializing intelligent connection pool...");
            
            // Load initial configuration
            currentConfig = loadDefaultConfiguration();
            
            // Create primary data sources
            createDataSource("primary-write", currentConfig.getPrimaryWriteConfig());
            createDataSource("primary-read", currentConfig.getPrimaryReadConfig());
            
            // Create read replicas if configured
            for (int i = 0; i < currentConfig.getReadReplicaCount(); i++) {
                createDataSource("read-replica-" + i, currentConfig.getReadReplicaConfig());
            }
            
            // Initialize connection health monitoring
            initializeHealthMonitoring();
            
            log.info("Intelligent connection pool initialized with {} data sources", 
                    dataSources.size());
            
        } catch (Exception e) {
            log.error("Failed to initialize intelligent connection pool", e);
            throw new RuntimeException("Connection pool initialization failed", e);
        }
    }
    
    /**
     * Gets a connection using intelligent routing
     */
    public Connection getConnection(QueryContext queryContext) throws SQLException {
        long startTime = System.currentTimeMillis();
        connectionRequestCount.incrementAndGet();
        
        try {
            // Determine best data source for this query
            String dataSourceKey = connectionRouter.routeQuery(queryContext, dataSources.keySet());
            
            // Get connection from selected data source
            HikariDataSource dataSource = dataSources.get(dataSourceKey);
            if (dataSource == null) {
                throw new SQLException("Data source not available: " + dataSourceKey);
            }
            
            Connection connection = dataSource.getConnection();
            
            // Wrap connection with monitoring
            connection = new MonitoredConnection(connection, dataSourceKey, queryContext);
            
            activeConnections.incrementAndGet();
            
            // Record metrics
            long waitTime = System.currentTimeMillis() - startTime;
            connectionWaitTime.addAndGet(waitTime);
            updateConnectionMetrics(dataSourceKey, waitTime, true);
            
            log.debug("Obtained connection from {} in {}ms for query type: {}", 
                     dataSourceKey, waitTime, queryContext.getQueryType());
            
            return connection;
            
        } catch (SQLException e) {
            updateConnectionMetrics("failed", System.currentTimeMillis() - startTime, false);
            log.error("Failed to get connection for query: " + queryContext.getQueryType(), e);
            throw e;
        }
    }
    
    /**
     * Gets a connection for a specific operation type
     */
    public Connection getConnection(String operationType) throws SQLException {
        QueryContext context = QueryContext.builder()
            .queryType(operationType)
            .readOnly(isReadOnlyOperation(operationType))
            .estimatedDuration(estimateOperationDuration(operationType))
            .priority(getOperationPriority(operationType))
            .build();
        
        return getConnection(context);
    }
    
    /**
     * Dynamic pool optimization based on predicted load
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void optimizePoolConfiguration() {
        try {
            // Get predicted load metrics
            PredictedLoadMetrics predictedLoad = queryPredictionService.getPredictedLoad();
            
            // Calculate optimal configuration
            ConnectionPoolConfiguration optimalConfig = 
                calculateOptimalConfiguration(predictedLoad);
            
            // Apply configuration if significant change
            if (shouldUpdateConfiguration(optimalConfig)) {
                updatePoolConfiguration(optimalConfig);
            }
            
            // Optimize individual pool sizes
            optimizeIndividualPools(predictedLoad);
            
        } catch (Exception e) {
            log.error("Error optimizing pool configuration", e);
        }
    }
    
    /**
     * Connection health monitoring and auto-healing
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorConnectionHealth() {
        try {
            for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
                String poolName = entry.getKey();
                HikariDataSource dataSource = entry.getValue();
                
                // Test connection health
                ConnectionHealthStatus health = testConnectionHealth(poolName, dataSource);
                connectionHealth.put(poolName, health);
                
                // Auto-heal unhealthy connections
                if (!health.isHealthy()) {
                    autoHealConnection(poolName, dataSource, health);
                }
                
                // Update pool metrics
                updatePoolMetrics(poolName, dataSource);
            }
            
        } catch (Exception e) {
            log.error("Error monitoring connection health", e);
        }
    }
    
    /**
     * Predictive connection pre-warming
     */
    @Scheduled(fixedRate = 120000) // Every 2 minutes
    public void preWarmConnections() {
        try {
            // Get upcoming query predictions
            List<PredictedQuery> upcomingQueries = queryPredictionService.getUpcomingQueries();
            
            for (PredictedQuery query : upcomingQueries) {
                if (query.getProbability() > 0.8) { // High probability queries
                    preWarmForQuery(query);
                }
            }
            
        } catch (Exception e) {
            log.error("Error pre-warming connections", e);
        }
    }
    
    /**
     * Creates a new data source with the given configuration
     */
    private void createDataSource(String name, DataSourceConfig config) {
        try {
            HikariConfig hikariConfig = new HikariConfig();
            
            // Basic configuration
            hikariConfig.setJdbcUrl(config.getJdbcUrl());
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setDriverClassName(config.getDriverClassName());
            
            // Pool sizing
            hikariConfig.setMinimumIdle(config.getMinIdle());
            hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
            
            // Connection timeout and validation
            hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
            hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
            hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
            hikariConfig.setValidationTimeout(config.getValidationTimeoutMs());
            
            // Connection testing
            hikariConfig.setConnectionTestQuery("SELECT 1");
            
            // Performance optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            
            // Pool name for monitoring
            hikariConfig.setPoolName("Waqiti-" + name);
            hikariConfig.setRegisterMbeans(true);
            
            // Create and register data source
            HikariDataSource dataSource = new HikariDataSource(hikariConfig);
            dataSources.put(name, dataSource);
            
            // Initialize metrics
            poolMetrics.put(name, new ConnectionPoolMetrics(name));
            
            log.info("Created data source: {} with pool size {}-{}", 
                    name, config.getMinIdle(), config.getMaxPoolSize());
            
        } catch (Exception e) {
            log.error("Failed to create data source: " + name, e);
            throw new RuntimeException("Data source creation failed", e);
        }
    }
    
    /**
     * Calculates optimal pool configuration based on predicted load
     */
    private ConnectionPoolConfiguration calculateOptimalConfiguration(PredictedLoadMetrics predictedLoad) {
        // Base calculations on predicted concurrent queries
        int predictedConcurrency = (int) Math.ceil(predictedLoad.getPeakConcurrentQueries());
        
        // Calculate optimal pool sizes with safety margins
        int optimalMinIdle = Math.max(5, predictedConcurrency / 4);
        int optimalMaxPoolSize = Math.max(10, (int) (predictedConcurrency * 1.5));
        
        // Adjust based on query types
        double readWriteRatio = predictedLoad.getReadWriteRatio();
        int writePoolSize = (int) Math.ceil(optimalMaxPoolSize / (1 + readWriteRatio));
        int readPoolSize = optimalMaxPoolSize - writePoolSize;
        
        // Connection timeouts based on predicted query durations
        long avgQueryDuration = predictedLoad.getAverageQueryDurationMs();
        long connectionTimeout = Math.max(30000, avgQueryDuration * 2);
        long idleTimeout = Math.max(600000, avgQueryDuration * 10);
        
        return ConnectionPoolConfiguration.builder()
            .primaryWriteConfig(DataSourceConfig.builder()
                .minIdle(Math.max(2, writePoolSize / 4))
                .maxPoolSize(writePoolSize)
                .connectionTimeoutMs(connectionTimeout)
                .idleTimeoutMs(idleTimeout)
                .build())
            .primaryReadConfig(DataSourceConfig.builder()
                .minIdle(Math.max(3, readPoolSize / 4))
                .maxPoolSize(readPoolSize)
                .connectionTimeoutMs(connectionTimeout)
                .idleTimeoutMs(idleTimeout)
                .build())
            .readReplicaConfig(DataSourceConfig.builder()
                .minIdle(Math.max(1, readPoolSize / 8))
                .maxPoolSize(Math.max(5, readPoolSize / 2))
                .connectionTimeoutMs(connectionTimeout)
                .idleTimeoutMs(idleTimeout)
                .build())
            .readReplicaCount(calculateOptimalReplicaCount(predictedLoad))
            .build();
    }
    
    /**
     * Tests connection health for a specific pool
     */
    private ConnectionHealthStatus testConnectionHealth(String poolName, HikariDataSource dataSource) {
        ConnectionHealthStatus.ConnectionHealthStatusBuilder statusBuilder = ConnectionHealthStatus.builder()
            .poolName(poolName)
            .testTime(Instant.now());
        
        try {
            // Test basic connectivity
            long startTime = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                long connectionTime = System.currentTimeMillis() - startTime;
                statusBuilder.connectionTimeMs(connectionTime);
                
                // Test query execution
                startTime = System.currentTimeMillis();
                try (var stmt = conn.prepareStatement("SELECT 1")) {
                    stmt.executeQuery();
                    long queryTime = System.currentTimeMillis() - startTime;
                    statusBuilder.queryTimeMs(queryTime);
                }
            }
            
            // Check pool statistics
            var poolStats = dataSource.getHikariPoolMXBean();
            statusBuilder
                .activeConnections(poolStats.getActiveConnections())
                .idleConnections(poolStats.getIdleConnections())
                .totalConnections(poolStats.getTotalConnections())
                .threadsAwaitingConnection(poolStats.getThreadsAwaitingConnection());
            
            // Determine health status
            boolean isHealthy = true;
            List<String> issues = new ArrayList<>();
            
            if (statusBuilder.build().getConnectionTimeMs() > 5000) {
                isHealthy = false;
                issues.add("Slow connection acquisition");
            }
            
            if (statusBuilder.build().getQueryTimeMs() > 1000) {
                isHealthy = false;
                issues.add("Slow query execution");
            }
            
            if (poolStats.getThreadsAwaitingConnection() > 5) {
                isHealthy = false;
                issues.add("High connection contention");
            }
            
            return statusBuilder
                .healthy(isHealthy)
                .issues(issues)
                .build();
            
        } catch (Exception e) {
            return statusBuilder
                .healthy(false)
                .issues(Arrays.asList("Connection test failed: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * Attempts to auto-heal unhealthy connections
     */
    private void autoHealConnection(String poolName, HikariDataSource dataSource, 
                                   ConnectionHealthStatus health) {
        try {
            log.warn("Attempting to auto-heal unhealthy pool: {} (issues: {})", 
                    poolName, health.getIssues());
            
            // Strategy 1: Soft refresh - evict idle connections
            dataSource.getHikariPoolMXBean().softEvictConnections();
            
            // Strategy 2: If still unhealthy, try pool refresh
            Thread.sleep(5000); // Wait for eviction to take effect
            ConnectionHealthStatus retest = testConnectionHealth(poolName, dataSource);
            
            if (!retest.isHealthy()) {
                // Strategy 3: Temporary pool resize
                var currentConfig = dataSource.getHikariConfigMXBean();
                int originalMax = currentConfig.getMaximumPoolSize();
                int originalMin = currentConfig.getMinimumIdle();
                
                // Temporarily reduce pool size to force connection renewal
                dataSource.setMaximumPoolSize(Math.max(1, originalMax / 2));
                dataSource.setMinimumIdle(Math.max(1, originalMin / 2));
                
                Thread.sleep(10000); // Wait for pool to adjust
                
                // Restore original size
                dataSource.setMaximumPoolSize(originalMax);
                dataSource.setMinimumIdle(originalMin);
                
                log.info("Applied auto-healing for pool: {}", poolName);
            }
            
        } catch (Exception e) {
            log.error("Auto-healing failed for pool: " + poolName, e);
        }
    }
    
    /**
     * Pre-warms connections for a predicted query
     */
    private void preWarmForQuery(PredictedQuery query) {
        try {
            QueryContext context = QueryContext.builder()
                .queryType(query.getQueryPattern())
                .readOnly(query.isReadOnly())
                .estimatedDuration(query.getEstimatedDurationMs())
                .priority(QueryPriority.LOW) // Pre-warming is low priority
                .build();
            
            String targetPool = connectionRouter.routeQuery(context, dataSources.keySet());
            HikariDataSource dataSource = dataSources.get(targetPool);
            
            if (dataSource != null) {
                // Pre-warm connection (get and immediately return)
                try (Connection conn = dataSource.getConnection()) {
                    // Connection is now warmed in the pool
                    log.debug("Pre-warmed connection for query: {} in pool: {}", 
                             query.getQueryPattern(), targetPool);
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to pre-warm connection for query: " + query.getQueryPattern(), e);
        }
    }
    
    // Helper methods and utility classes
    
    private ConnectionPoolConfiguration loadDefaultConfiguration() {
        return ConnectionPoolConfiguration.builder()
            .primaryWriteConfig(DataSourceConfig.defaultWriteConfig())
            .primaryReadConfig(DataSourceConfig.defaultReadConfig())
            .readReplicaConfig(DataSourceConfig.defaultReadReplicaConfig())
            .readReplicaCount(2)
            .build();
    }
    
    private void initializeHealthMonitoring() {
        // Initialize health monitoring for all pools
        for (String poolName : dataSources.keySet()) {
            connectionHealth.put(poolName, ConnectionHealthStatus.builder()
                .poolName(poolName)
                .healthy(true)
                .testTime(Instant.now())
                .issues(new ArrayList<>())
                .build());
        }
    }
    
    private boolean shouldUpdateConfiguration(ConnectionPoolConfiguration newConfig) {
        if (currentConfig == null) return true;
        
        // Check if enough time has passed since last update
        long timeSinceLastUpdate = System.currentTimeMillis() - lastConfigUpdate.get();
        if (timeSinceLastUpdate < 300000) return false; // Min 5 minutes between updates
        
        // Check if configuration change is significant
        return isSignificantConfigurationChange(currentConfig, newConfig);
    }
    
    private boolean isSignificantConfigurationChange(ConnectionPoolConfiguration current, 
                                                   ConnectionPoolConfiguration proposed) {
        // Consider change significant if pool size difference > 20%
        double writePoolChange = Math.abs(
            current.getPrimaryWriteConfig().getMaxPoolSize() - 
            proposed.getPrimaryWriteConfig().getMaxPoolSize()) / 
            (double) current.getPrimaryWriteConfig().getMaxPoolSize();
        
        double readPoolChange = Math.abs(
            current.getPrimaryReadConfig().getMaxPoolSize() - 
            proposed.getPrimaryReadConfig().getMaxPoolSize()) / 
            (double) current.getPrimaryReadConfig().getMaxPoolSize();
        
        return writePoolChange > 0.2 || readPoolChange > 0.2;
    }
    
    private void updatePoolConfiguration(ConnectionPoolConfiguration newConfig) {
        try {
            log.info("Updating connection pool configuration based on predicted load");
            
            // Update existing pools
            updateDataSourceConfig("primary-write", newConfig.getPrimaryWriteConfig());
            updateDataSourceConfig("primary-read", newConfig.getPrimaryReadConfig());
            
            // Update read replicas
            for (int i = 0; i < newConfig.getReadReplicaCount(); i++) {
                String poolName = "read-replica-" + i;
                if (dataSources.containsKey(poolName)) {
                    updateDataSourceConfig(poolName, newConfig.getReadReplicaConfig());
                } else {
                    createDataSource(poolName, newConfig.getReadReplicaConfig());
                }
            }
            
            currentConfig = newConfig;
            lastConfigUpdate.set(System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("Failed to update pool configuration", e);
        }
    }
    
    private void updateDataSourceConfig(String poolName, DataSourceConfig config) {
        HikariDataSource dataSource = dataSources.get(poolName);
        if (dataSource != null) {
            dataSource.setMaximumPoolSize(config.getMaxPoolSize());
            dataSource.setMinimumIdle(config.getMinIdle());
            // Note: Other properties like timeouts cannot be changed on existing pools
        }
    }
    
    private void optimizeIndividualPools(PredictedLoadMetrics predictedLoad) {
        // Optimize each pool individually based on its specific load patterns
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            String poolName = entry.getKey();
            HikariDataSource dataSource = entry.getValue();
            
            optimizeSinglePool(poolName, dataSource, predictedLoad);
        }
    }
    
    private void optimizeSinglePool(String poolName, HikariDataSource dataSource, 
                                   PredictedLoadMetrics predictedLoad) {
        // Get pool-specific metrics
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
        if (metrics == null) return;
        
        // Calculate optimal size for this specific pool
        double utilizationRate = metrics.getAverageUtilization();
        int currentMax = dataSource.getMaximumPoolSize();
        
        int optimalSize = currentMax;
        if (utilizationRate > 0.8) {
            optimalSize = (int) Math.ceil(currentMax * 1.2); // Increase by 20%
        } else if (utilizationRate < 0.3) {
            optimalSize = Math.max(5, (int) Math.ceil(currentMax * 0.8)); // Decrease by 20%
        }
        
        if (optimalSize != currentMax) {
            dataSource.setMaximumPoolSize(optimalSize);
            log.debug("Optimized pool size for {}: {} -> {} (utilization: {:.1f}%)", 
                     poolName, currentMax, optimalSize, utilizationRate * 100);
        }
    }
    
    private void updateConnectionMetrics(String poolName, long waitTime, boolean successful) {
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
        if (metrics != null) {
            metrics.recordConnectionRequest(waitTime, successful);
        }
    }
    
    private void updatePoolMetrics(String poolName, HikariDataSource dataSource) {
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
        if (metrics != null) {
            var poolStats = dataSource.getHikariPoolMXBean();
            metrics.updatePoolStatistics(
                poolStats.getTotalConnections(),
                poolStats.getActiveConnections(),
                poolStats.getIdleConnections(),
                poolStats.getThreadsAwaitingConnection()
            );
        }
    }
    
    private boolean isReadOnlyOperation(String operationType) {
        return operationType.toLowerCase().startsWith("select") ||
               operationType.toLowerCase().startsWith("get") ||
               operationType.toLowerCase().startsWith("find") ||
               operationType.toLowerCase().startsWith("search");
    }
    
    private long estimateOperationDuration(String operationType) {
        // Simple estimation based on operation type
        switch (operationType.toLowerCase()) {
            case "select": return 100L;
            case "insert": return 200L;
            case "update": return 300L;
            case "delete": return 250L;
            default: return 150L;
        }
    }
    
    private QueryPriority getOperationPriority(String operationType) {
        // Assign priority based on operation type
        if (operationType.toLowerCase().contains("payment") ||
            operationType.toLowerCase().contains("transaction")) {
            return QueryPriority.HIGH;
        } else if (operationType.toLowerCase().contains("analytics") ||
                  operationType.toLowerCase().contains("report")) {
            return QueryPriority.LOW;
        }
        return QueryPriority.MEDIUM;
    }
    
    private int calculateOptimalReplicaCount(PredictedLoadMetrics predictedLoad) {
        // Calculate optimal number of read replicas based on read/write ratio
        double readWriteRatio = predictedLoad.getReadWriteRatio();
        if (readWriteRatio > 5.0) return 3; // High read workload
        else if (readWriteRatio > 2.0) return 2; // Medium read workload
        else return 1; // Low read workload
    }
    
    // Getters for monitoring and metrics
    
    public Map<String, ConnectionPoolMetrics> getPoolMetrics() {
        return new HashMap<>(poolMetrics);
    }
    
    public Map<String, ConnectionHealthStatus> getConnectionHealth() {
        return new HashMap<>(connectionHealth);
    }
    
    public int getTotalConnections() {
        return totalConnections.get();
    }
    
    public int getActiveConnections() {
        return activeConnections.get();
    }
    
    public long getConnectionRequestCount() {
        return connectionRequestCount.get();
    }
    
    public double getAverageConnectionWaitTime() {
        long requests = connectionRequestCount.get();
        return requests > 0 ? connectionWaitTime.get() / (double) requests : 0.0;
    }
}