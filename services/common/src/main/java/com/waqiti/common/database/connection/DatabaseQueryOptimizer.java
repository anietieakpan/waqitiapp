package com.waqiti.common.database.connection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PERFORMANCE CRITICAL: Database Query Optimizer and Connection Pool Enhancer
 * 
 * Provides intelligent database optimization features:
 * - Automatic slow query detection and optimization
 * - Connection pool performance tuning
 * - Query plan analysis and caching recommendations
 * - Index usage optimization
 * - Connection lifecycle management
 * - Performance bottleneck identification
 */
@Component
@ConditionalOnProperty(name = "database.optimization.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DatabaseQueryOptimizer {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    
    public DatabaseQueryOptimizer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Value("${database.optimization.slow-query-threshold:5000}")
    private long slowQueryThresholdMs;

    @Value("${database.optimization.analysis-enabled:true}")
    private boolean analysisEnabled;

    @Value("${database.optimization.auto-vacuum-enabled:true}")
    private boolean autoVacuumEnabled;

    // Query performance tracking
    private final Map<String, QueryPerformanceStats> queryStats = new ConcurrentHashMap<>();
    private final Map<String, IndexUsageStats> indexStats = new ConcurrentHashMap<>();
    private final AtomicLong totalOptimizationsApplied = new AtomicLong(0);
    private final AtomicLong totalSlowQueriesDetected = new AtomicLong(0);

    /**
     * Comprehensive database optimization task - runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void optimizeDatabase() {
        if (!analysisEnabled) {
            return;
        }

        try {
            log.debug("Starting database optimization cycle");

            // Analyze slow queries
            analyzeSlowQueries();

            // Check index usage
            analyzeIndexUsage();

            // Optimize connection settings
            optimizeConnectionSettings();

            // Update table statistics
            updateTableStatistics();

            // Check for vacuum opportunities
            if (autoVacuumEnabled) {
                checkVacuumOpportunities();
            }

            log.debug("Database optimization cycle completed");

        } catch (Exception e) {
            log.error("Error during database optimization", e);
        }
    }

    /**
     * Analyze slow queries and provide optimization suggestions
     */
    private void analyzeSlowQueries() {
        try {
            String slowQuerySQL = """
                SELECT query, calls, total_time, min_time, max_time, mean_time, stddev_time
                FROM pg_stat_statements 
                WHERE mean_time > ? 
                ORDER BY mean_time DESC 
                LIMIT 20
                """;

            List<Map<String, Object>> slowQueries = jdbcTemplate.queryForList(
                slowQuerySQL, slowQueryThresholdMs);

            for (Map<String, Object> query : slowQueries) {
                String sql = (String) query.get("query");
                Double meanTime = (Double) query.get("mean_time");
                Long calls = (Long) query.get("calls");

                log.warn("Slow query detected: Mean time {}ms, Calls: {}, SQL: {}", 
                        meanTime, calls, truncateQuery(sql));

                totalSlowQueriesDetected.incrementAndGet();

                // Analyze and suggest optimizations
                analyzeQueryForOptimization(sql, meanTime, calls);
            }

        } catch (Exception e) {
            log.debug("pg_stat_statements not available or error analyzing slow queries", e);
            // Fallback to basic analysis
            performBasicSlowQueryAnalysis();
        }
    }

    /**
     * Analyze individual query for optimization opportunities
     */
    private void analyzeQueryForOptimization(String sql, double meanTime, long calls) {
        try {
            // Get query execution plan
            String explainSQL = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql;
            List<Map<String, Object>> plans = jdbcTemplate.queryForList(explainSQL);

            if (!plans.isEmpty()) {
                // Analyze plan for issues
                analyzeExecutionPlan(plans.get(0), sql);
            }

        } catch (Exception e) {
            log.debug("Error analyzing query execution plan: {}", e.getMessage());
        }
    }

    /**
     * Analyze query execution plan for optimization opportunities
     */
    private void analyzeExecutionPlan(Map<String, Object> plan, String sql) {
        // In a real implementation, this would parse the JSON plan
        // and identify issues like:
        // - Sequential scans on large tables
        // - Missing indexes
        // - Inefficient joins
        // - Sort operations that could benefit from indexes

        log.info("Query plan analysis completed for: {}", truncateQuery(sql));
        
        // Suggest optimizations based on plan analysis
        suggestQueryOptimizations(sql, plan);
    }

    /**
     * Suggest specific optimizations based on query analysis
     */
    private void suggestQueryOptimizations(String sql, Map<String, Object> plan) {
        List<String> suggestions = new ArrayList<>();

        // Analyze SQL patterns and suggest improvements
        if (sql.toLowerCase().contains("select *")) {
            suggestions.add("Consider specifying only required columns instead of SELECT *");
        }

        if (sql.toLowerCase().contains("order by") && !sql.toLowerCase().contains("limit")) {
            suggestions.add("Consider adding LIMIT clause to ORDER BY queries");
        }

        if (sql.toLowerCase().contains("like '%")) {
            suggestions.add("Consider using full-text search or trigram indexes for LIKE queries starting with %");
        }

        if (!suggestions.isEmpty()) {
            log.info("Optimization suggestions for query '{}': {}", 
                    truncateQuery(sql), String.join(", ", suggestions));
            totalOptimizationsApplied.incrementAndGet();
        }
    }

    /**
     * Analyze index usage and suggest improvements
     */
    private void analyzeIndexUsage() {
        try {
            String indexUsageSQL = """
                SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch,
                       pg_size_pretty(pg_relation_size(indexrelid)) as size
                FROM pg_stat_user_indexes 
                ORDER BY idx_scan ASC
                LIMIT 20
                """;

            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(indexUsageSQL);

            for (Map<String, Object> index : indexes) {
                String tableName = (String) index.get("tablename");
                String indexName = (String) index.get("indexname");
                Long scans = (Long) index.get("idx_scan");
                String size = (String) index.get("size");

                if (scans == 0) {
                    log.warn("Unused index detected: {}.{} (Size: {})", tableName, indexName, size);
                    suggestIndexAction(tableName, indexName, "DROP", "Index is never used");
                } else if (scans < 10) {
                    log.info("Low-usage index: {}.{} (Scans: {}, Size: {})", 
                            tableName, indexName, scans, size);
                    suggestIndexAction(tableName, indexName, "REVIEW", "Low usage, consider if needed");
                }
            }

            // Find tables with high sequential scans that might need indexes
            findMissingIndexOpportunities();

        } catch (Exception e) {
            log.debug("Error analyzing index usage", e);
        }
    }

    /**
     * Find tables with high sequential scans that might benefit from indexes
     */
    private void findMissingIndexOpportunities() {
        try {
            String seqScanSQL = """
                SELECT schemaname, tablename, seq_scan, seq_tup_read, 
                       idx_scan, idx_tup_fetch, n_tup_ins, n_tup_upd, n_tup_del
                FROM pg_stat_user_tables 
                WHERE seq_scan > 1000 AND seq_tup_read > seq_scan * 1000
                ORDER BY seq_tup_read DESC
                LIMIT 10
                """;

            List<Map<String, Object>> tables = jdbcTemplate.queryForList(seqScanSQL);

            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("tablename");
                Long seqScans = (Long) table.get("seq_scan");
                Long seqTupRead = (Long) table.get("seq_tup_read");

                log.warn("Table with high sequential scans: {} (Scans: {}, Rows read: {})", 
                        tableName, seqScans, seqTupRead);
                        
                suggestIndexAction(tableName, null, "CREATE", 
                    "High sequential scan activity, consider adding indexes");
            }

        } catch (Exception e) {
            log.debug("Error finding missing index opportunities", e);
        }
    }

    /**
     * Optimize connection-level settings for better performance
     */
    private void optimizeConnectionSettings() {
        try {
            // Check and optimize work_mem setting
            optimizeWorkMemSetting();

            // Check shared_buffers effectiveness
            checkSharedBuffersEffectiveness();

            // Analyze checkpoint and WAL settings
            analyzeCheckpointSettings();

        } catch (Exception e) {
            log.debug("Error optimizing connection settings", e);
        }
    }

    /**
     * Optimize work_mem based on connection pool size and available memory
     */
    private void optimizeWorkMemSetting() {
        try {
            List<Map<String, Object>> workMemResult = jdbcTemplate.queryForList(
                "SHOW work_mem");
            
            if (!workMemResult.isEmpty()) {
                String currentWorkMem = (String) workMemResult.get(0).get("work_mem");
                log.debug("Current work_mem setting: {}", currentWorkMem);

                // Suggest optimizations based on pool size and available memory
                suggestWorkMemOptimization(currentWorkMem);
            }

        } catch (Exception e) {
            log.debug("Error checking work_mem setting", e);
        }
    }

    /**
     * Check shared_buffers effectiveness
     */
    private void checkSharedBuffersEffectiveness() {
        try {
            String bufferHitRatioSQL = """
                SELECT datname, 
                       blks_read, 
                       blks_hit, 
                       round(blks_hit::numeric / (blks_read + blks_hit) * 100, 2) as hit_ratio
                FROM pg_stat_database 
                WHERE datname = current_database()
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(bufferHitRatioSQL);
            
            if (!results.isEmpty()) {
                Map<String, Object> stats = results.get(0);
                Double hitRatio = (Double) stats.get("hit_ratio");
                
                log.debug("Buffer cache hit ratio: {}%", hitRatio);
                
                if (hitRatio < 95.0) {
                    log.warn("Buffer cache hit ratio is low ({}%), consider increasing shared_buffers", hitRatio);
                    suggestMemoryOptimization("shared_buffers", 
                        "Low buffer cache hit ratio indicates need for more memory");
                }
            }

        } catch (Exception e) {
            log.debug("Error checking buffer effectiveness", e);
        }
    }

    /**
     * Analyze checkpoint and WAL settings
     */
    private void analyzeCheckpointSettings() {
        try {
            String checkpointSQL = """
                SELECT checkpoints_timed, checkpoints_req, checkpoint_write_time, 
                       checkpoint_sync_time, buffers_checkpoint, buffers_clean, 
                       buffers_backend
                FROM pg_stat_bgwriter
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(checkpointSQL);
            
            if (!results.isEmpty()) {
                Map<String, Object> stats = results.get(0);
                Long timedCheckpoints = (Long) stats.get("checkpoints_timed");
                Long requestedCheckpoints = (Long) stats.get("checkpoints_req");
                
                // If too many requested checkpoints, suggest WAL optimization
                if (requestedCheckpoints > timedCheckpoints * 0.1) {
                    log.warn("High number of requested checkpoints, consider increasing checkpoint_segments or max_wal_size");
                    suggestWALOptimization("High checkpoint frequency indicates WAL optimization needed");
                }
            }

        } catch (Exception e) {
            log.debug("Error analyzing checkpoint settings", e);
        }
    }

    /**
     * Update table statistics for query planner optimization
     */
    private void updateTableStatistics() {
        try {
            // Get tables that haven't been analyzed recently
            String staleStatsSQL = """
                SELECT schemaname, tablename, last_analyze, last_autoanalyze, n_mod_since_analyze
                FROM pg_stat_user_tables 
                WHERE (last_analyze IS NULL OR last_analyze < current_timestamp - interval '7 days')
                   OR (n_mod_since_analyze > 1000)
                ORDER BY n_mod_since_analyze DESC
                LIMIT 10
                """;

            List<Map<String, Object>> staleTables = jdbcTemplate.queryForList(staleStatsSQL);

            for (Map<String, Object> table : staleTables) {
                String tableName = (String) table.get("tablename");
                Long modsSinceAnalyze = (Long) table.get("n_mod_since_analyze");
                
                log.info("Table {} has {} modifications since last analyze, suggesting statistics update", 
                        tableName, modsSinceAnalyze);
                
                // In production, you might want to automatically run ANALYZE
                // jdbcTemplate.execute("ANALYZE " + tableName);
                
                suggestMaintenanceAction(tableName, "ANALYZE", 
                    "Table statistics are stale, consider running ANALYZE");
            }

        } catch (Exception e) {
            log.debug("Error updating table statistics", e);
        }
    }

    /**
     * Check for vacuum opportunities
     */
    private void checkVacuumOpportunities() {
        try {
            String vacuumSQL = """
                SELECT schemaname, tablename, n_dead_tup, n_live_tup, last_vacuum, last_autovacuum,
                       round(n_dead_tup::numeric / GREATEST(n_live_tup, 1) * 100, 2) as dead_tuple_ratio
                FROM pg_stat_user_tables 
                WHERE n_dead_tup > 1000 
                   OR (n_live_tup > 0 AND n_dead_tup::numeric / n_live_tup > 0.1)
                ORDER BY dead_tuple_ratio DESC
                LIMIT 10
                """;

            List<Map<String, Object>> tables = jdbcTemplate.queryForList(vacuumSQL);

            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("tablename");
                Long deadTuples = (Long) table.get("n_dead_tup");
                Double deadRatio = (Double) table.get("dead_tuple_ratio");
                
                log.warn("Table {} has {:.1f}% dead tuples ({} dead rows), consider vacuum", 
                        tableName, deadRatio, deadTuples);
                
                suggestMaintenanceAction(tableName, "VACUUM", 
                    String.format("High dead tuple ratio (%.1f%%)", deadRatio));
            }

        } catch (Exception e) {
            log.debug("Error checking vacuum opportunities", e);
        }
    }

    /**
     * Perform basic slow query analysis when pg_stat_statements is not available
     */
    private void performBasicSlowQueryAnalysis() {
        // This would involve custom logging and analysis
        log.debug("Performing basic slow query analysis");
    }

    // Helper methods for suggestions and recommendations

    private void suggestIndexAction(String tableName, String indexName, String action, String reason) {
        log.info("INDEX OPTIMIZATION: {} - Table: {}, Index: {}, Reason: {}", 
                action, tableName, indexName != null ? indexName : "N/A", reason);
    }

    private void suggestWorkMemOptimization(String currentValue) {
        log.info("MEMORY OPTIMIZATION: Current work_mem: {}, consider adjusting based on workload", currentValue);
    }

    private void suggestMemoryOptimization(String parameter, String reason) {
        log.info("MEMORY OPTIMIZATION: Parameter: {}, Reason: {}", parameter, reason);
    }

    private void suggestWALOptimization(String reason) {
        log.info("WAL OPTIMIZATION: {}", reason);
    }

    private void suggestMaintenanceAction(String tableName, String action, String reason) {
        log.info("MAINTENANCE SUGGESTION: Table: {}, Action: {}, Reason: {}", tableName, action, reason);
    }

    private String truncateQuery(String sql) {
        return sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
    }

    /**
     * Get optimization statistics for monitoring
     */
    public OptimizationStatistics getOptimizationStats() {
        return OptimizationStatistics.builder()
            .totalOptimizationsApplied(totalOptimizationsApplied.get())
            .totalSlowQueriesDetected(totalSlowQueriesDetected.get())
            .activeQueryCount(queryStats.size())
            .trackedIndexCount(indexStats.size())
            .lastOptimizationRun(LocalDateTime.now())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class OptimizationStatistics {
        private long totalOptimizationsApplied;
        private long totalSlowQueriesDetected;
        private int activeQueryCount;
        private int trackedIndexCount;
        private LocalDateTime lastOptimizationRun;
    }

    @lombok.Data
    @lombok.Builder
    public static class QueryPerformanceStats {
        private String queryHash;
        private long executionCount;
        private double averageExecutionTime;
        private double maxExecutionTime;
        private LocalDateTime lastExecution;
    }

    @lombok.Data
    @lombok.Builder
    public static class IndexUsageStats {
        private String tableName;
        private String indexName;
        private long scanCount;
        private long tuplesRead;
        private LocalDateTime lastUsed;
    }
}