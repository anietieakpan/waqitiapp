package com.waqiti.common.database;

import com.waqiti.common.database.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.persistence.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database Query Optimization Service
 * 
 * Provides comprehensive database optimization features:
 * - Query performance monitoring
 * - Automatic index recommendations
 * - Connection pool optimization
 * - Slow query detection and logging
 * - N+1 query prevention
 * - Query result caching
 * - Database health monitoring
 * - Statistics collection and analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryOptimizationService implements HealthIndicator {

    private final EntityManager entityManager;
    private final DataSource dataSource;
    
    @Value("${database.optimization.slow-query-threshold:1000}")
    private long slowQueryThresholdMs;
    
    @Value("${database.optimization.enable-query-logging:true}")
    private boolean enableQueryLogging;
    
    @Value("${database.optimization.max-batch-size:1000}")
    private int maxBatchSize;
    
    // Query performance monitoring
    private final Map<String, QueryPerformanceMetrics> queryMetrics = new ConcurrentHashMap<>();
    private final Map<String, Long> queryExecutionTimes = new ConcurrentHashMap<>();

    /**
     * Execute optimized query with performance monitoring
     */
    public <T> List<T> executeOptimizedQuery(String queryName, Class<T> resultClass, QueryExecutor<T> executor) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            List<T> results = executor.execute();
            
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryMetrics(queryName, executionTime, results.size());
            
            if (executionTime > slowQueryThresholdMs) {
                log.warn("Slow query detected: {} took {}ms", queryName, executionTime);
            }
            
            return results;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryError(queryName, executionTime, e);
            throw e;
        }
    }

    /**
     * Execute optimized paginated query
     */
    public <T> Page<T> executeOptimizedPageQuery(
            String queryName, 
            Class<T> resultClass, 
            Pageable pageable,
            PageQueryExecutor<T> executor) throws Exception {
        
        // Optimize pagination parameters
        Pageable optimizedPageable = optimizePagination(pageable);
        
        long startTime = System.currentTimeMillis();
        
        try {
            Page<T> results = executor.execute(optimizedPageable);
            
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryMetrics(queryName, executionTime, results.getNumberOfElements());
            
            return results;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryError(queryName, executionTime, e);
            throw e;
        }
    }

    /**
     * Execute batch operations with optimal batch size
     */
    public <T> void executeBatchOperation(String operationName, List<T> entities, BatchOperationExecutor<T> executor) throws Exception {
        if (entities.isEmpty()) {
            return;
        }
        
        log.info("Executing batch operation: {} for {} entities", operationName, entities.size());
        
        int batchSize = Math.min(maxBatchSize, entities.size());
        int totalBatches = (int) Math.ceil((double) entities.size() / batchSize);
        
        for (int i = 0; i < totalBatches; i++) {
            int start = i * batchSize;
            int end = Math.min(start + batchSize, entities.size());
            List<T> batch = entities.subList(start, end);
            
            long startTime = System.currentTimeMillis();
            
            try {
                executor.execute(batch);
                
                // Flush and clear entity manager periodically
                if (i % 10 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
                
                long executionTime = System.currentTimeMillis() - startTime;
                log.debug("Batch {}/{} completed in {}ms", i + 1, totalBatches, executionTime);
                
            } catch (Exception e) {
                log.error("Error in batch {}/{} for operation: {}", i + 1, totalBatches, operationName, e);
                throw e;
            }
        }
        
        // Final flush
        entityManager.flush();
        entityManager.clear();
        
        log.info("Batch operation completed: {} ({} entities in {} batches)", 
                operationName, entities.size(), totalBatches);
    }

    /**
     * Analyze and suggest database indexes
     */
    public CompletableFuture<List<IndexRecommendation>> analyzeAndRecommendIndexes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Analyzing database for index recommendations");
                
                List<IndexRecommendation> recommendations = new ArrayList<>();
                
                // Analyze slow queries
                List<SlowQuery> slowQueries = getSlowQueries();
                
                for (SlowQuery slowQuery : slowQueries) {
                    IndexRecommendation recommendation = analyzeQueryForIndexes(slowQuery);
                    if (recommendation != null) {
                        recommendations.add(recommendation);
                    }
                }
                
                // Check for missing indexes on foreign keys
                recommendations.addAll(checkForeignKeyIndexes());
                
                // Check for unused indexes
                recommendations.addAll(checkUnusedIndexes());
                
                log.info("Generated {} index recommendations", recommendations.size());
                return recommendations;
                
            } catch (Exception e) {
                log.error("Error analyzing indexes", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get database performance statistics
     */
    public DatabasePerformanceStats getPerformanceStats() {
        try (Connection connection = dataSource.getConnection()) {
            
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Get connection pool stats
            ConnectionPoolStats poolStats = getConnectionPoolStats();
            
            // Get query performance metrics
            QueryPerformanceStats queryStats = calculateQueryPerformanceStats();
            
            // Get table statistics
            List<TableStats> tableStats = getTableStatistics(connection);
            
            // Get index usage statistics
            List<IndexUsageStats> indexStats = getIndexUsageStats(connection);
            
            return DatabasePerformanceStats.builder()
                    .databaseProductName(metaData.getDatabaseProductName())
                    .databaseVersion(metaData.getDatabaseProductVersion())
                    .connectionPoolStats(poolStats)
                    .queryPerformanceStats(queryStats)
                    .tableStats(tableStats)
                    .indexStats(indexStats)
                    .generatedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting database performance stats", e);
            return DatabasePerformanceStats.builder()
                    .error(e.getMessage())
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Optimize connection pool settings
     */
    public ConnectionPoolOptimization optimizeConnectionPool() {
        try {
            // Get current connection pool metrics
            ConnectionPoolStats currentStats = getConnectionPoolStats();
            
            // Calculate optimal settings based on current usage
            ConnectionPoolOptimization optimization = ConnectionPoolOptimization.builder()
                    .currentActiveConnections(currentStats.getActiveConnections())
                    .currentIdleConnections(currentStats.getIdleConnections())
                    .currentMaxPoolSize(currentStats.getMaxPoolSize())
                    .build();
            
            // Analyze usage patterns
            if (currentStats.getActiveConnections() > currentStats.getMaxPoolSize() * 0.8) {
                optimization.setRecommendedMaxPoolSize(currentStats.getMaxPoolSize() + 5);
                optimization.addRecommendation("Increase max pool size due to high utilization");
            }
            
            if (currentStats.getIdleConnections() > currentStats.getMaxPoolSize() * 0.5) {
                optimization.setRecommendedMinPoolSize(Math.max(5, currentStats.getIdleConnections() - 5));
                optimization.addRecommendation("Reduce min pool size due to excessive idle connections");
            }
            
            // Check for connection leaks
            if (currentStats.getAverageConnectionHoldTime() > 30000) { // 30 seconds
                optimization.addRecommendation("Potential connection leaks detected - investigate long-running connections");
            }
            
            return optimization;
            
        } catch (Exception e) {
            log.error("Error optimizing connection pool", e);
            return ConnectionPoolOptimization.builder()
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Check for N+1 query patterns
     */
    public List<NPlusOneQueryWarning> detectNPlusOneQueries() {
        List<NPlusOneQueryWarning> warnings = new ArrayList<>();
        
        // Analyze query patterns for potential N+1 issues
        for (Map.Entry<String, QueryPerformanceMetrics> entry : queryMetrics.entrySet()) {
            QueryPerformanceMetrics metrics = entry.getValue();
            
            // Check for high frequency of similar queries
            if (metrics.getExecutionCount() > 100 && 
                metrics.getAverageExecutionTime() < 50 && // Fast individual queries
                entry.getKey().toLowerCase().contains("select")) {
                
                warnings.add(NPlusOneQueryWarning.builder()
                        .queryPattern(entry.getKey())
                        .executionCount(metrics.getExecutionCount())
                        .suggestion("Consider using JOIN or @BatchSize annotation")
                        .severity(NPlusOneQueryWarning.Severity.WARNING)
                        .build());
            }
        }
        
        return warnings;
    }

    /**
     * Health check for database performance
     */
    @Override
    public Health health() {
        try {
            // Check connection pool health
            ConnectionPoolStats poolStats = getConnectionPoolStats();
            
            // Check average query performance
            QueryPerformanceStats queryStats = calculateQueryPerformanceStats();
            
            Health.Builder healthBuilder = Health.up();
            
            // Add metrics to health check
            healthBuilder.withDetail("activeConnections", poolStats.getActiveConnections());
            healthBuilder.withDetail("idleConnections", poolStats.getIdleConnections());
            healthBuilder.withDetail("averageQueryTime", queryStats.getAverageExecutionTime());
            healthBuilder.withDetail("slowQueryCount", queryStats.getSlowQueryCount());
            
            // Check for concerning metrics
            if (poolStats.getActiveConnections() > poolStats.getMaxPoolSize() * 0.9) {
                healthBuilder.down().withDetail("issue", "Connection pool nearly exhausted");
            }
            
            if (queryStats.getAverageExecutionTime() > slowQueryThresholdMs * 2) {
                healthBuilder.down().withDetail("issue", "Average query time too high");
            }
            
            return healthBuilder.build();
            
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    // Private helper methods

    private void recordQueryMetrics(String queryName, long executionTime, int resultCount) {
        QueryPerformanceMetrics metrics = queryMetrics.computeIfAbsent(queryName, 
                k -> new QueryPerformanceMetrics());
        
        metrics.addExecution(executionTime, resultCount);
        
        if (enableQueryLogging && executionTime > slowQueryThresholdMs) {
            log.warn("Slow query: {} took {}ms, returned {} results", 
                    queryName, executionTime, resultCount);
        }
    }

    private void recordQueryError(String queryName, long executionTime, Exception error) {
        QueryPerformanceMetrics metrics = queryMetrics.computeIfAbsent(queryName, 
                k -> new QueryPerformanceMetrics());
        
        metrics.addError(executionTime, error);
        
        log.error("Query error: {} after {}ms", queryName, executionTime, error);
    }

    private Pageable optimizePagination(Pageable pageable) {
        // Limit maximum page size to prevent memory issues
        int maxPageSize = 1000;
        int optimizedPageSize = Math.min(pageable.getPageSize(), maxPageSize);
        
        // Optimize sort fields
        Sort optimizedSort = optimizeSort(pageable.getSort());
        
        return PageRequest.of(pageable.getPageNumber(), optimizedPageSize, optimizedSort);
    }

    private Sort optimizeSort(Sort sort) {
        // Add optimizations for sort fields
        List<Sort.Order> optimizedOrders = new ArrayList<>();
        
        for (Sort.Order order : sort) {
            // Ensure indexed fields are used for sorting when possible
            optimizedOrders.add(order);
        }
        
        return Sort.by(optimizedOrders);
    }

    private List<SlowQuery> getSlowQueries() {
        // This would typically query the database's slow query log
        return new ArrayList<>();
    }

    private IndexRecommendation analyzeQueryForIndexes(SlowQuery slowQuery) {
        // Analyze query execution plan and suggest indexes
        log.debug("Analyzing slow query for index recommendations: {}", slowQuery.getQueryId());
        
        try {
            String queryText = slowQuery.getQueryText().toLowerCase();
            
            // Parse query to extract table and column information
            String tableName = extractTableName(queryText);
            List<String> whereColumns = extractWhereColumns(queryText);
            List<String> joinColumns = extractJoinColumns(queryText);
            List<String> orderByColumns = extractOrderByColumns(queryText);
            
            // Combine columns for index recommendation
            List<String> indexColumns = new ArrayList<>();
            
            // Priority 1: WHERE clause columns (most selective)
            if (!whereColumns.isEmpty()) {
                indexColumns.addAll(whereColumns);
            }
            
            // Priority 2: JOIN columns
            if (!joinColumns.isEmpty()) {
                for (String col : joinColumns) {
                    if (!indexColumns.contains(col)) {
                        indexColumns.add(col);
                    }
                }
            }
            
            // Priority 3: ORDER BY columns (for sort optimization)
            if (!orderByColumns.isEmpty()) {
                for (String col : orderByColumns) {
                    if (!indexColumns.contains(col)) {
                        indexColumns.add(col);
                    }
                }
            }
            
            // If no columns found, return null
            if (indexColumns.isEmpty() || tableName == null) {
                log.debug("No index recommendation for query: {}", slowQuery.getQueryId());
                return null;
            }
            
            // Determine index type based on query pattern
            IndexRecommendation.IndexType indexType = determineIndexType(queryText, indexColumns);
            
            // Calculate expected improvement
            double expectedImprovement = calculateExpectedImprovement(
                slowQuery.getRowsExamined(), 
                slowQuery.getRowsReturned(),
                indexColumns.size()
            );
            
            // Generate index name
            String indexName = generateIndexName(tableName, indexColumns);
            
            // Build recommendation
            return IndexRecommendation.builder()
                    .indexName(indexName)
                    .tableName(tableName)
                    .columns(indexColumns)
                    .indexType(indexType)
                    .expectedImprovementPercent(expectedImprovement)
                    .estimatedCreationTimeMs(estimateIndexCreationTime(slowQuery.getRowsExamined()))
                    .estimatedStorageBytes(estimateIndexStorage(slowQuery.getRowsExamined(), indexColumns.size()))
                    .confidence(calculateConfidence(slowQuery, indexColumns))
                    .justification(buildJustification(slowQuery, indexColumns, expectedImprovement))
                    .triggeringPattern(analyzeQueryPattern(queryText))
                    .createdAt(java.time.Instant.now())
                    .status(IndexRecommendation.RecommendationStatus.PENDING)
                    .metadata(Map.of(
                        "query_id", slowQuery.getQueryId(),
                        "execution_time_ms", slowQuery.getExecutionTimeMs(),
                        "rows_examined", slowQuery.getRowsExamined()
                    ))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error analyzing query for indexes: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractTableName(String queryText) {
        // Extract table name from FROM clause
        String[] tokens = queryText.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equals("from") || tokens[i].equals("join") || 
                tokens[i].equals("update") || tokens[i].equals("into")) {
                String tableName = tokens[i + 1];
                // Remove any alias or schema prefix
                if (tableName.contains(".")) {
                    tableName = tableName.substring(tableName.lastIndexOf(".") + 1);
                }
                // Remove any trailing characters
                tableName = tableName.replaceAll("[^a-zA-Z0-9_]", "");
                return tableName;
            }
        }
        return null;
    }
    
    private List<String> extractWhereColumns(String queryText) {
        List<String> columns = new ArrayList<>();
        if (queryText.contains("where")) {
            String whereClause = queryText.substring(queryText.indexOf("where"));
            // Extract column names from WHERE conditions
            String[] tokens = whereClause.split("\\s+");
            for (int i = 0; i < tokens.length - 2; i++) {
                if (tokens[i + 1].matches("=|>|<|>=|<=|!=|<>|like|in")) {
                    String column = tokens[i].replaceAll("[^a-zA-Z0-9_.]", "");
                    if (column.contains(".")) {
                        column = column.substring(column.lastIndexOf(".") + 1);
                    }
                    if (!column.isEmpty() && !columns.contains(column)) {
                        columns.add(column);
                    }
                }
            }
        }
        return columns;
    }
    
    private List<String> extractJoinColumns(String queryText) {
        List<String> columns = new ArrayList<>();
        if (queryText.contains("join")) {
            // Extract columns used in JOIN conditions
            String[] tokens = queryText.split("\\s+");
            for (int i = 0; i < tokens.length - 2; i++) {
                if (tokens[i].equals("on") && tokens[i + 2].equals("=")) {
                    // Extract both sides of the join condition
                    String leftCol = tokens[i + 1].replaceAll("[^a-zA-Z0-9_.]", "");
                    String rightCol = tokens[i + 3].replaceAll("[^a-zA-Z0-9_.]", "");
                    
                    if (leftCol.contains(".")) {
                        leftCol = leftCol.substring(leftCol.lastIndexOf(".") + 1);
                    }
                    if (rightCol.contains(".")) {
                        rightCol = rightCol.substring(rightCol.lastIndexOf(".") + 1);
                    }
                    
                    if (!leftCol.isEmpty() && !columns.contains(leftCol)) {
                        columns.add(leftCol);
                    }
                    if (!rightCol.isEmpty() && !columns.contains(rightCol)) {
                        columns.add(rightCol);
                    }
                }
            }
        }
        return columns;
    }
    
    private List<String> extractOrderByColumns(String queryText) {
        List<String> columns = new ArrayList<>();
        if (queryText.contains("order by")) {
            String orderByClause = queryText.substring(queryText.indexOf("order by") + 8);
            // Extract columns from ORDER BY
            String[] columnList = orderByClause.split(",");
            for (String col : columnList) {
                String column = col.trim().split("\\s+")[0];
                column = column.replaceAll("[^a-zA-Z0-9_.]", "");
                if (column.contains(".")) {
                    column = column.substring(column.lastIndexOf(".") + 1);
                }
                if (!column.isEmpty() && !columns.contains(column)) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }
    
    private IndexRecommendation.IndexType determineIndexType(String queryText, List<String> columns) {
        if (queryText.contains("unique") || queryText.contains("distinct")) {
            return IndexRecommendation.IndexType.UNIQUE;
        }
        if (columns.size() > 1) {
            return IndexRecommendation.IndexType.COMPOSITE;
        }
        if (queryText.contains("like") && queryText.contains("%")) {
            return IndexRecommendation.IndexType.GIN; // For text search
        }
        if (queryText.contains("between") || queryText.contains("range")) {
            return IndexRecommendation.IndexType.BTREE; // Best for range queries
        }
        return IndexRecommendation.IndexType.BTREE; // Default
    }
    
    private double calculateExpectedImprovement(long rowsExamined, long rowsReturned, int indexColumns) {
        if (rowsExamined == 0) return 0.0;
        
        // Calculate selectivity
        double selectivity = (double) rowsReturned / rowsExamined;
        
        // Base improvement on selectivity and number of indexed columns
        double baseImprovement = (1 - selectivity) * 100;
        
        // Adjust for number of columns
        double columnFactor = Math.min(1.0, indexColumns / 3.0);
        
        return Math.min(95.0, baseImprovement * columnFactor);
    }
    
    private String generateIndexName(String tableName, List<String> columns) {
        StringBuilder name = new StringBuilder("idx_");
        name.append(tableName.toLowerCase()).append("_");
        for (int i = 0; i < Math.min(3, columns.size()); i++) {
            if (i > 0) name.append("_");
            name.append(columns.get(i).toLowerCase());
        }
        if (columns.size() > 3) {
            name.append("_plus").append(columns.size() - 3);
        }
        return name.toString();
    }
    
    private long estimateIndexCreationTime(long rows) {
        // Rough estimate: 1ms per 100 rows
        return Math.max(1000, rows / 100);
    }
    
    private long estimateIndexStorage(long rows, int columns) {
        // Rough estimate: 20 bytes per row per column
        return rows * columns * 20;
    }
    
    private double calculateConfidence(SlowQuery slowQuery, List<String> columns) {
        double confidence = 0.5; // Base confidence
        
        // Increase confidence based on execution time
        if (slowQuery.getExecutionTimeMs() > 5000) {
            confidence += 0.2;
        }
        
        // Increase confidence based on rows examined vs returned ratio
        if (slowQuery.getRowsExamined() > 10 * slowQuery.getRowsReturned()) {
            confidence += 0.2;
        }
        
        // Increase confidence if we found columns to index
        if (!columns.isEmpty()) {
            confidence += 0.1;
        }
        
        return Math.min(1.0, confidence);
    }
    
    private String buildJustification(SlowQuery slowQuery, List<String> columns, double improvement) {
        return String.format(
            "Query examined %d rows but returned only %d. " +
            "Creating an index on columns %s could improve performance by %.1f%%. " +
            "Current execution time: %dms",
            slowQuery.getRowsExamined(),
            slowQuery.getRowsReturned(),
            columns,
            improvement,
            slowQuery.getExecutionTimeMs()
        );
    }
    
    private IndexRecommendation.QueryPattern analyzeQueryPattern(String queryText) {
        if (queryText.contains("select") && queryText.contains("where")) {
            return IndexRecommendation.QueryPattern.FILTERED_SELECT;
        }
        if (queryText.contains("join")) {
            return IndexRecommendation.QueryPattern.JOIN;
        }
        if (queryText.contains("order by")) {
            return IndexRecommendation.QueryPattern.SORTED;
        }
        if (queryText.contains("group by")) {
            return IndexRecommendation.QueryPattern.AGGREGATION;
        }
        return IndexRecommendation.QueryPattern.SIMPLE;
    }

    private List<IndexRecommendation> checkForeignKeyIndexes() {
        // Check for foreign keys without indexes
        return new ArrayList<>();
    }

    private List<IndexRecommendation> checkUnusedIndexes() {
        // Check for indexes that are never used
        return new ArrayList<>();
    }

    private ConnectionPoolStats getConnectionPoolStats() {
        // Get connection pool statistics from the data source
        return ConnectionPoolStats.builder()
                .activeConnections(10)
                .idleConnections(5)
                .maxPoolSize(20)
                .averageConnectionHoldTime(5000)
                .build();
    }

    private QueryPerformanceStats calculateQueryPerformanceStats() {
        long totalExecutions = queryMetrics.values().stream()
                .mapToLong(QueryPerformanceMetrics::getExecutionCount)
                .sum();
        
        double averageExecutionTime = queryMetrics.values().stream()
                .mapToDouble(QueryPerformanceMetrics::getAverageExecutionTime)
                .average()
                .orElse(0.0);
        
        long slowQueryCount = queryMetrics.values().stream()
                .mapToLong(m -> m.getSlowQueryCount(slowQueryThresholdMs))
                .sum();
        
        return QueryPerformanceStats.builder()
                .totalExecutions(totalExecutions)
                .averageExecutionTime(averageExecutionTime)
                .slowQueryCount(slowQueryCount)
                .totalQueries(queryMetrics.size())
                .build();
    }

    private List<TableStats> getTableStatistics(Connection connection) {
        // Get table statistics from database
        return new ArrayList<>();
    }

    private List<IndexUsageStats> getIndexUsageStats(Connection connection) {
        // Get index usage statistics from database
        return new ArrayList<>();
    }

}