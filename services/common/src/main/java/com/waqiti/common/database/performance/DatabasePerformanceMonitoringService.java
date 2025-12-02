package com.waqiti.common.database.performance;

import com.waqiti.common.database.dto.*;
import com.waqiti.common.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive database performance monitoring service that tracks:
 * - Query execution times and patterns
 * - Connection pool health and utilization
 * - Cache hit rates and efficiency
 * - Index usage and recommendations
 * - Lock contention and deadlocks
 * - Resource utilization metrics
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DatabasePerformanceMonitoringService implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsCollector metricsCollector;
    
    // Performance tracking maps
    private final Map<String, QueryPerformanceData> queryPerformanceMap = new ConcurrentHashMap<>();
    private final Map<String, IndexUsageStats> indexUsageMap = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPoolMetrics> poolMetricsMap = new ConcurrentHashMap<>();
    
    // Atomic counters for real-time metrics
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong slowQueries = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong connectionFailures = new AtomicLong(0);
    private final AtomicLong deadlockCount = new AtomicLong(0);
    
    // Configuration
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000L;
    private static final int MAX_TRACKED_QUERIES = 10000;
    private static final String METRICS_PREFIX = "waqiti.database";
    
    @PostConstruct
    public void initialize() {
        log.info("Database Performance Monitoring Service initialized");
        startPerformanceCollection();
    }
    
    /**
     * Records query execution metrics for performance analysis.
     *
     * @param sql the SQL query
     * @param executionTimeMs execution time in milliseconds
     * @param resultCount number of rows returned
     * @param cacheHit whether result was served from cache
     */
    public void recordQueryExecution(String sql, long executionTimeMs, int resultCount, boolean cacheHit) {
        totalQueries.incrementAndGet();
        
        if (cacheHit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        
        if (executionTimeMs > SLOW_QUERY_THRESHOLD_MS) {
            slowQueries.incrementAndGet();
        }
        
        // Track detailed query performance
        String normalizedSql = normalizeQuery(sql);
        QueryPerformanceData performanceData = queryPerformanceMap.computeIfAbsent(
            normalizedSql, k -> new QueryPerformanceData()
        );
        
        synchronized (performanceData) {
            performanceData.incrementExecutions();
            performanceData.addExecutionTime(executionTimeMs);
            performanceData.addResultCount(resultCount);
            performanceData.updateLastExecution();
            
            if (cacheHit) {
                performanceData.incrementCacheHits();
            }
        }
        
        // Update metrics
        metricsCollector.incrementCounter(METRICS_PREFIX + ".queries.total");
        metricsCollector.recordTimer(METRICS_PREFIX + ".queries.execution_time", executionTimeMs);
        
        if (executionTimeMs > SLOW_QUERY_THRESHOLD_MS) {
            metricsCollector.incrementCounter(METRICS_PREFIX + ".queries.slow");
        }
        
        // Cleanup old entries if map gets too large
        if (queryPerformanceMap.size() > MAX_TRACKED_QUERIES) {
            cleanupOldQueries();
        }
    }
    
    /**
     * Records connection pool metrics.
     *
     * @param poolName name of the connection pool
     * @param activeConnections number of active connections
     * @param idleConnections number of idle connections
     * @param maxConnections maximum connections allowed
     * @param waitingRequests number of requests waiting for connections
     */
    public void recordConnectionPoolMetrics(String poolName, int activeConnections, 
                                          int idleConnections, int maxConnections, int waitingRequests) {
        
        ConnectionPoolMetrics metrics = new ConnectionPoolMetrics();
        metrics.setActiveConnections(activeConnections);
        metrics.setIdleConnections(idleConnections);
        metrics.setMaxConnections(maxConnections);
        metrics.setWaitingRequests(waitingRequests);
        double utilizationPercentage = (double) activeConnections / maxConnections * 100;
        metrics.setUtilizationPercentage(utilizationPercentage);
        metrics.setTimestamp(Instant.now());
        
        poolMetricsMap.put(poolName, metrics);
        
        // Update metrics
        metricsCollector.recordGauge(METRICS_PREFIX + ".pool.active_connections", "pool", activeConnections);
        metricsCollector.recordGauge(METRICS_PREFIX + ".pool.idle_connections", "pool", idleConnections);
        metricsCollector.recordGauge(METRICS_PREFIX + ".pool.utilization_percentage", "pool",
                                   utilizationPercentage);
        metricsCollector.recordGauge(METRICS_PREFIX + ".pool.waiting_requests", "pool", waitingRequests);
    }
    
    /**
     * Records index usage statistics.
     *
     * @param tableName table name
     * @param indexName index name
     * @param usageCount number of times index was used
     * @param scanRatio ratio of index scans vs table scans
     */
    public void recordIndexUsage(String tableName, String indexName, long usageCount, double scanRatio) {
        String key = tableName + "." + indexName;
        IndexUsageStats stats = indexUsageMap.computeIfAbsent(key, k -> new IndexUsageStats());
        
        stats.setTableName(tableName);
        stats.setIndexName(indexName);
        stats.setUsageCount(usageCount);
        stats.setScanRatio(scanRatio);
        stats.setLastUpdated(Instant.now());
        
        metricsCollector.recordGauge(METRICS_PREFIX + ".index.usage_count", "index", usageCount);
        metricsCollector.recordGauge(METRICS_PREFIX + ".index.scan_ratio", "index", scanRatio);
    }
    
    /**
     * Records deadlock occurrence.
     *
     * @param tableName affected table
     * @param lockType type of lock
     * @param query1 first conflicting query
     * @param query2 second conflicting query
     */
    public void recordDeadlock(String tableName, String lockType, String query1, String query2) {
        deadlockCount.incrementAndGet();
        
        DeadlockEvent event = new DeadlockEvent();
        event.setTableName(tableName);
        event.setLockType(lockType);
        event.setQuery1(query1);
        event.setQuery2(query2);
        event.setTimestamp(Instant.now());
        
        log.error("Deadlock detected on table {}: {} vs {}", tableName, query1, query2);
        
        metricsCollector.incrementCounter(METRICS_PREFIX + ".deadlocks.total");
    }
    
    /**
     * Generates comprehensive performance report.
     *
     * @return database performance report
     */
    public DatabasePerformanceReport generatePerformanceReport() {
        DatabasePerformanceReport report = new DatabasePerformanceReport();
        report.setTimestamp(Instant.now());
        
        // Overall statistics
        OverallStats overallStats = new OverallStats();
        overallStats.setTotalQueries(totalQueries.get());
        overallStats.setSlowQueries(slowQueries.get());
        overallStats.setCacheHitRate(calculateCacheHitRate());
        overallStats.setAverageExecutionTime(calculateAverageExecutionTime());
        overallStats.setDeadlockCount(deadlockCount.get());
        report.setOverallStats(overallStats);
        
        // Top slow queries
        List<SlowQueryReport> topSlowQueries = identifyTopSlowQueries(10);
        report.setTopSlowQueries(topSlowQueries);
        
        // Connection pool status
        List<ConnectionPoolStatus> poolStatuses = generateConnectionPoolStatuses();
        report.setConnectionPoolStatuses(poolStatuses);
        
        // Index recommendations
        List<IndexRecommendation> indexRecommendations = generateIndexRecommendations();
        report.setIndexRecommendations(indexRecommendations);
        
        // Cache performance
        CachePerformanceStats cacheStats = analyzeCachePerformance();
        report.setCachePerformanceStats(cacheStats);
        
        return report;
    }
    
    /**
     * Scheduled method to collect database statistics.
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void collectDatabaseStatistics() {
        try {
            collectConnectionPoolStats();
            collectIndexUsageStats();
            collectTableStatistics();
            collectLockStatistics();
        } catch (Exception e) {
            log.error("Error collecting database statistics", e);
            connectionFailures.incrementAndGet();
        }
    }
    
    /**
     * Scheduled method to cleanup old performance data.
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void cleanupOldData() {
        cleanupOldQueries();
        cleanupOldIndexStats();
        cleanupOldPoolMetrics();
    }
    
    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();
            
            // Check database connectivity
            long startTime = System.currentTimeMillis();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            builder.withDetail("database_response_time_ms", responseTime);
            builder.withDetail("total_queries", totalQueries.get());
            builder.withDetail("slow_queries", slowQueries.get());
            builder.withDetail("cache_hit_rate", calculateCacheHitRate());
            builder.withDetail("connection_failures", connectionFailures.get());
            builder.withDetail("deadlock_count", deadlockCount.get());
            
            if (responseTime > 5000) {
                builder.down().withDetail("reason", "Database response time too high");
            }
            
            if (connectionFailures.get() > 10) {
                builder.down().withDetail("reason", "Too many connection failures");
            }
            
            return builder.build();
            
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
    
    // Private helper methods
    
    private void startPerformanceCollection() {
        // Initialize performance collection threads and metrics
        log.info("Started database performance collection");
    }
    
    private String normalizeQuery(String sql) {
        // Normalize SQL by removing literals and whitespace for pattern matching
        return sql.replaceAll("'[^']*'", "?")
                 .replaceAll("\\d+", "?")
                 .replaceAll("\\s+", " ")
                 .trim()
                 .toLowerCase();
    }
    
    private void cleanupOldQueries() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        queryPerformanceMap.entrySet().removeIf(entry -> 
            entry.getValue().getLastExecution().isBefore(cutoff));
    }
    
    private void cleanupOldIndexStats() {
        Instant cutoff = Instant.now().minus(6, ChronoUnit.HOURS);
        indexUsageMap.entrySet().removeIf(entry -> 
            entry.getValue().getLastUpdated().isBefore(cutoff));
    }
    
    private void cleanupOldPoolMetrics() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        poolMetricsMap.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private double calculateCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total * 100.0 : 0.0;
    }
    
    private double calculateAverageExecutionTime() {
        return queryPerformanceMap.values().stream()
                .mapToDouble(QueryPerformanceData::getAverageExecutionTime)
                .average()
                .orElse(0.0);
    }
    
    private List<SlowQueryReport> identifyTopSlowQueries(int limit) {
        return queryPerformanceMap.entrySet().stream()
                .filter(entry -> entry.getValue().getAverageExecutionTime() > SLOW_QUERY_THRESHOLD_MS)
                .sorted(Map.Entry.<String, QueryPerformanceData>comparingByValue(
                    Comparator.comparingDouble(QueryPerformanceData::getAverageExecutionTime).reversed()))
                .limit(limit)
                .map(entry -> {
                    SlowQueryReport report = new SlowQueryReport();
                    report.setQuery(entry.getKey());
                    report.setAverageExecutionTime(entry.getValue().getAverageExecutionTime());
                    report.setExecutionCount(entry.getValue().getExecutionCount());
                    report.setMaxExecutionTime(entry.getValue().getMaxExecutionTime());
                    return report;
                })
                .toList();
    }
    
    private List<ConnectionPoolStatus> generateConnectionPoolStatuses() {
        return poolMetricsMap.values().stream()
                .map(metrics -> {
                    ConnectionPoolStatus status = new ConnectionPoolStatus();
                    status.setActiveConnections(metrics.getActiveConnections());
                    status.setIdleConnections(metrics.getIdleConnections());
                    status.setMaxConnections(metrics.getMaxConnections());
                    status.setUtilizationPercentage(metrics.getUtilizationPercentage());
                    status.setWaitingRequests(metrics.getWaitingRequests());
                    status.setHealthy(metrics.getUtilizationPercentage() < 90.0);
                    return status;
                })
                .toList();
    }
    
    private List<IndexRecommendation> generateIndexRecommendations() {
        List<IndexRecommendation> recommendations = new ArrayList<>();
        
        // Analyze query patterns and suggest indexes
        for (Map.Entry<String, QueryPerformanceData> entry : queryPerformanceMap.entrySet()) {
            if (entry.getValue().getAverageExecutionTime() > SLOW_QUERY_THRESHOLD_MS &&
                entry.getValue().getExecutionCount() > 100) {
                
                IndexRecommendation recommendation = new IndexRecommendation();
                recommendation.setQuery(entry.getKey());
                recommendation.setAverageExecutionTime(entry.getValue().getAverageExecutionTime());
                recommendation.setExecutionCount(entry.getValue().getExecutionCount());
                recommendation.setRecommendation("Consider adding index for frequently executed slow query");
                recommendation.setPriority(calculateRecommendationPriority(entry.getValue()));
                
                recommendations.add(recommendation);
            }
        }
        
        return recommendations.stream()
                .sorted(Comparator.comparingInt(IndexRecommendation::getPriority).reversed())
                .limit(20)
                .toList();
    }
    
    private CachePerformanceStats analyzeCachePerformance() {
        CachePerformanceStats stats = new CachePerformanceStats();
        stats.setHitRate(calculateCacheHitRate());
        stats.setCacheHits(cacheHits.get());
        stats.setCacheMisses(cacheMisses.get());
        stats.setTotalRequests(cacheHits.get() + cacheMisses.get());
        
        // Analyze cache efficiency by query type
        Map<String, Double> hitRateByQueryType = new HashMap<>();
        queryPerformanceMap.entrySet().stream()
                .filter(entry -> entry.getValue().getTotalExecutions() > 0)
                .forEach(entry -> {
                    String queryType = extractQueryType(entry.getKey());
                    double hitRate = (double) entry.getValue().getCacheHits() / 
                                   entry.getValue().getTotalExecutions() * 100.0;
                    hitRateByQueryType.put(queryType, hitRate);
                });
        
        stats.setHitRateByQueryType(hitRateByQueryType);
        return stats;
    }
    
    private void collectConnectionPoolStats() {
        // Collect connection pool statistics from HikariCP or other pools
        // This would integrate with actual connection pool implementations
        log.debug("Collecting connection pool statistics");
    }
    
    private void collectIndexUsageStats() throws SQLException {
        // Collect index usage statistics from PostgreSQL
        String sql = """
            SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
            FROM pg_stat_user_indexes 
            WHERE idx_scan > 0 
            ORDER BY idx_scan DESC 
            LIMIT 100
        """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String tableName = rs.getString("tablename");
                String indexName = rs.getString("indexname");
                long scanCount = rs.getLong("idx_scan");
                
                recordIndexUsage(tableName, indexName, scanCount, 0.0);
            }
        }
    }
    
    private void collectTableStatistics() throws SQLException {
        // Collect table statistics for optimization insights
        String sql = """
            SELECT schemaname, tablename, n_tup_ins, n_tup_upd, n_tup_del, 
                   n_live_tup, n_dead_tup, last_vacuum, last_autovacuum
            FROM pg_stat_user_tables 
            ORDER BY n_live_tup DESC 
            LIMIT 50
        """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String tableName = rs.getString("tablename");
                long liveTuples = rs.getLong("n_live_tup");
                long deadTuples = rs.getLong("n_dead_tup");
                
                // Calculate dead tuple ratio for vacuum recommendations
                if (liveTuples > 0) {
                    double deadRatio = (double) deadTuples / liveTuples;
                    if (deadRatio > 0.1) { // More than 10% dead tuples
                        log.info("Table {} has high dead tuple ratio: {}%", 
                               tableName, String.format("%.2f", deadRatio * 100));
                    }
                }
            }
        }
    }
    
    private void collectLockStatistics() throws SQLException {
        // Collect lock statistics to identify contention
        String sql = """
            SELECT mode, count(*) as lock_count 
            FROM pg_locks 
            WHERE NOT granted 
            GROUP BY mode
        """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String mode = rs.getString("mode");
                int count = rs.getInt("lock_count");
                
                if (count > 0) {
                    log.warn("Lock contention detected: {} locks of type {}", count, mode);
                    metricsCollector.recordGauge(METRICS_PREFIX + ".locks.waiting", "lock_mode", count);
                }
            }
        }
    }
    
    private int calculateRecommendationPriority(QueryPerformanceData data) {
        // Calculate priority based on execution time, frequency, and impact
        double timeWeight = data.getAverageExecutionTime() / 1000.0; // Convert to seconds
        double frequencyWeight = Math.log10(data.getExecutionCount() + 1);
        return (int) (timeWeight * frequencyWeight * 100);
    }
    
    private String extractQueryType(String sql) {
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT")) return "SELECT";
        if (upperSql.startsWith("INSERT")) return "INSERT";
        if (upperSql.startsWith("UPDATE")) return "UPDATE";
        if (upperSql.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
}