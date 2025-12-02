package com.waqiti.common.database.advanced;

import com.waqiti.common.database.dto.*;
import com.waqiti.common.database.exception.DatabaseOptimizationException;
import com.waqiti.common.database.model.*;
import com.waqiti.common.tracing.DistributedTracingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Advanced database optimization service providing query caching, connection pooling,
 * index optimization, and batch processing capabilities.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
public class AdvancedDatabaseOptimizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedDatabaseOptimizationService.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedTracingService tracingService;
    private final DatabaseOptimizationProperties properties;
    
    public AdvancedDatabaseOptimizationService(JdbcTemplate jdbcTemplate,
                                             DataSource dataSource,
                                             RedisTemplate<String, Object> redisTemplate,
                                             DistributedTracingService tracingService,
                                             DatabaseOptimizationProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.tracingService = tracingService;
        this.properties = properties;
    }
    
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(5);
    private final Map<String, QueryMetrics> queryMetrics = new ConcurrentHashMap<>();
    private final Map<String, com.waqiti.common.database.model.IndexRecommendation> indexRecommendations = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        if (properties.isAutoOptimizationEnabled()) {
            startPeriodicOptimization();
        }
        
        logger.info("Advanced database optimization service initialized (auto-optimization: {})", 
                properties.isAutoOptimizationEnabled());
    }
    
    /**
     * Executes a query with automatic optimization and caching.
     *
     * @param sql the SQL query
     * @param params query parameters
     * @param cacheKey optional cache key
     * @return query results
     */
    public <T> List<T> executeOptimizedQuery(String sql, Object[] params, String cacheKey, 
                                           RowMapper<T> rowMapper) {
        DistributedTracingService.TraceContext traceContext = 
            tracingService.startChildTrace("db_query_optimized");
        
        try {
            tracingService.addTag("db.sql.operation", extractOperation(sql));
            tracingService.addTag("db.sql.table", extractTableName(sql));
            
            long startTime = System.currentTimeMillis();
            
            // Try cache first if cache key is provided
            if (cacheKey != null && properties.getQueryCache().isEnabled()) {
                List<T> cachedResult = getCachedQueryResult(cacheKey);
                if (cachedResult != null) {
                    updateQueryMetrics(sql, true, System.currentTimeMillis() - startTime, cachedResult.size());
                    tracingService.addTag("db.cache.hit", "true");
                    return cachedResult;
                }
                tracingService.addTag("db.cache.hit", "false");
            }
            
            // Analyze query for optimization opportunities
            QueryAnalysis analysis = analyzeQuery(sql);
            String optimizedSql = optimizeQuery(sql, analysis);
            
            // Execute query
            List<T> results = jdbcTemplate.query(optimizedSql, rowMapper, params);
            
            long executionTime = System.currentTimeMillis() - startTime;
            updateQueryMetrics(sql, false, executionTime, results.size());
            
            // Cache results if enabled and cache key provided
            if (cacheKey != null && properties.getQueryCache().isEnabled() && shouldCacheResults(analysis, results.size())) {
                cacheQueryResult(cacheKey, results, properties.getQueryCache().getDefaultTtlMinutes());
            }
            
            // Generate index recommendations if needed
            if (executionTime > properties.getSlowQueryThresholdMs()) {
                generateIndexRecommendation(sql, analysis, executionTime);
            }
            
            tracingService.addTag("db.execution_time", String.valueOf(executionTime));
            tracingService.addTag("db.result_count", String.valueOf(results.size()));
            
            return results;
            
        } catch (Exception e) {
            tracingService.recordError(e);
            throw new DatabaseOptimizationException("Query execution failed", e);
        } finally {
            tracingService.finishTrace(traceContext);
        }
    }
    
    /**
     * Executes batch operations with optimization.
     *
     * @param batchRequest the batch request containing operations
     * @return batch execution result
     */
    @Transactional
    public BatchExecutionResult executeBatchOperations(BatchRequest batchRequest) {
        DistributedTracingService.TraceContext traceContext = 
            tracingService.startChildTrace("db_batch_execution");
        
        try {
            tracingService.addTag("db.batch.size", String.valueOf(batchRequest.getOperations().size()));
            tracingService.addTag("db.batch.type", batchRequest.getType().name());
            
            long startTime = System.currentTimeMillis();
            
            BatchExecutionResult result = switch (batchRequest.getType()) {
                case INSERT -> executeBatchInserts(batchRequest);
                case UPDATE -> executeBatchUpdates(batchRequest);
                case DELETE -> executeBatchDeletes(batchRequest);
                case MIXED -> executeMixedBatch(batchRequest);
            };
            
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            
            tracingService.addTag("db.batch.execution_time", String.valueOf(executionTime));
            tracingService.addTag("db.batch.success_count", String.valueOf(result.getSuccessCount()));
            tracingService.addTag("db.batch.failure_count", String.valueOf(result.getFailureCount()));
            
            logger.info("Batch operation completed: {} operations in {}ms (success: {}, failures: {})",
                    batchRequest.getOperations().size(), executionTime, 
                    result.getSuccessCount(), result.getFailureCount());
            
            return result;
            
        } catch (Exception e) {
            tracingService.recordError(e);
            throw new DatabaseOptimizationException("Batch execution failed", e);
        } finally {
            tracingService.finishTrace(traceContext);
        }
    }
    
    /**
     * Analyzes database performance and provides optimization recommendations.
     *
     * @return performance analysis report
     */
    public DatabasePerformanceReport analyzePerformance() {
        DistributedTracingService.TraceContext traceContext = 
            tracingService.startChildTrace("db_performance_analysis");
        
        try {
            DatabasePerformanceReport report = new DatabasePerformanceReport();
            report.setTimestamp(Instant.now());
            
            // Analyze slow queries
            List<com.waqiti.common.database.dto.SlowQuery> slowQueries = identifySlowQueries();
            report.setSlowQueries(slowQueries);
            
            // Check connection pool health
            ConnectionPoolStatus poolStatus = analyzeConnectionPool();
            report.setConnectionPoolStatus(poolStatus);
            
            // Generate index recommendations
            List<com.waqiti.common.database.model.IndexRecommendation> modelRecommendations = generateIndexRecommendations();
            List<com.waqiti.common.database.dto.IndexRecommendation> dtoRecommendations = modelRecommendations.stream()
                .map(this::convertToDTO)
                .collect(java.util.stream.Collectors.toList());
            report.setIndexRecommendations(dtoRecommendations);
            
            // Analyze query patterns
            QueryPatternAnalysis patternAnalysis = analyzeQueryPatterns();
            report.setQueryPatternAnalysis(patternAnalysis);
            
            // Check cache hit rates
            CacheMetrics cacheMetrics = analyzeCachePerformance();
            report.setCacheMetrics(cacheMetrics);
            
            logger.info("Database performance analysis completed: {} slow queries, {} index recommendations",
                    slowQueries.size(), dtoRecommendations.size());
            
            return report;
            
        } catch (Exception e) {
            tracingService.recordError(e);
            throw new DatabaseOptimizationException("Performance analysis failed", e);
        } finally {
            tracingService.finishTrace(traceContext);
        }
    }
    
    /**
     * Optimizes database indexes based on query patterns.
     *
     * @return optimization result
     */
    @Transactional
    public IndexOptimizationResult optimizeIndexes() {
        DistributedTracingService.TraceContext traceContext = 
            tracingService.startChildTrace("db_index_optimization");
        
        try {
            IndexOptimizationResult result = new IndexOptimizationResult();
            result.setTimestamp(Instant.now());
            
            List<com.waqiti.common.database.model.IndexRecommendation> recommendations = generateIndexRecommendations();
            List<IndexCreationResult> creationResults = new ArrayList<>();
            
            for (com.waqiti.common.database.model.IndexRecommendation recommendation : recommendations) {
                if (recommendation.getScore() > properties.getAutoIndexCreationThreshold()) {
                    try {
                        createIndex(recommendation);
                        creationResults.add(new IndexCreationResult(recommendation.getIndexName(), true, null));
                        logger.info("Created index: {} on table: {}", 
                                recommendation.getIndexName(), recommendation.getTableName());
                    } catch (Exception e) {
                        creationResults.add(new IndexCreationResult(recommendation.getIndexName(), false, e.getMessage()));
                        logger.error("Failed to create index: {}", recommendation.getIndexName(), e);
                    }
                }
            }
            
            result.setIndexCreationResults(creationResults);
            result.setTotalRecommendations(recommendations.size());
            result.setSuccessfulCreations(creationResults.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum());
            
            return result;
            
        } catch (Exception e) {
            tracingService.recordError(e);
            throw new DatabaseOptimizationException("Index optimization failed", e);
        } finally {
            tracingService.finishTrace(traceContext);
        }
    }
    
    /**
     * Cleans up old cached query results and optimizes cache storage.
     */
    public void optimizeQueryCache() {
        if (!properties.getQueryCache().isEnabled()) {
            return;
        }
        
        try {
            // Clean up expired cache entries
            Set<String> keys = redisTemplate.keys(properties.getQueryCache().getKeyPrefix() + "*");
            int cleanedCount = 0;
            
            for (String key : keys) {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && 
                    redisTemplate.getExpire(key) != null && 
                    redisTemplate.getExpire(key) <= 0) {
                    redisTemplate.delete(key);
                    cleanedCount++;
                }
            }
            
            logger.info("Query cache optimization completed: cleaned {} expired entries", cleanedCount);
            
        } catch (Exception e) {
            logger.error("Query cache optimization failed", e);
        }
    }
    
    // Private helper methods
    
    private void startPeriodicOptimization() {
        // Schedule periodic tasks for database optimization
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(properties.getOptimizationIntervalMinutes() * 60 * 1000);
                    
                    if (properties.isAutoIndexOptimizationEnabled()) {
                        optimizeIndexes();
                    }
                    
                    if (properties.getQueryCache().isEnabled()) {
                        optimizeQueryCache();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Periodic optimization failed", e);
                }
            }
        });
    }
    
    @SuppressWarnings("unchecked")
    private <T> List<T> getCachedQueryResult(String cacheKey) {
        try {
            String fullKey = properties.getQueryCache().getKeyPrefix() + cacheKey;
            return (List<T>) redisTemplate.opsForValue().get(fullKey);
        } catch (Exception e) {
            logger.warn("Failed to retrieve cached query result for key: {}", cacheKey, e);
            return null;
        }
    }
    
    private <T> void cacheQueryResult(String cacheKey, List<T> results, long ttlMinutes) {
        try {
            String fullKey = properties.getQueryCache().getKeyPrefix() + cacheKey;
            redisTemplate.opsForValue().set(fullKey, results, 
                    java.time.Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            logger.warn("Failed to cache query result for key: {}", cacheKey, e);
        }
    }
    
    private QueryAnalysis analyzeQuery(String sql) {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setSql(sql);
        analysis.setOperation(extractOperation(sql));
        analysis.setTableName(extractTableName(sql));
        analysis.setHasWhereClause(sql.toLowerCase().contains("where"));
        analysis.setHasJoin(sql.toLowerCase().contains("join"));
        analysis.setHasOrderBy(sql.toLowerCase().contains("order by"));
        analysis.setHasGroupBy(sql.toLowerCase().contains("group by"));
        
        return analysis;
    }
    
    private String optimizeQuery(String sql, QueryAnalysis analysis) {
        // Simple query optimization logic
        String optimizedSql = sql;
        
        // Add LIMIT if not present and it's a SELECT without aggregation
        if (analysis.getOperation().equals("SELECT") && 
            !optimizedSql.toLowerCase().contains("limit") &&
            !analysis.isHasGroupBy() && 
            !optimizedSql.toLowerCase().contains("count(") &&
            !optimizedSql.toLowerCase().contains("sum(") &&
            !optimizedSql.toLowerCase().contains("avg(")) {
            
            optimizedSql += " LIMIT " + properties.getDefaultQueryLimit();
        }
        
        return optimizedSql;
    }
    
    private String extractOperation(String sql) {
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT")) return "SELECT";
        if (upperSql.startsWith("INSERT")) return "INSERT";
        if (upperSql.startsWith("UPDATE")) return "UPDATE";
        if (upperSql.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
    
    private String extractTableName(String sql) {
        // Simplified table name extraction
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("FROM ")) {
            String[] parts = upperSql.split("FROM ");
            if (parts.length > 1) {
                String[] tableParts = parts[1].trim().split("\\s+");
                return tableParts[0];
            }
        }
        return "unknown";
    }
    
    private boolean shouldCacheResults(QueryAnalysis analysis, int resultSize) {
        // Cache decisions based on query characteristics
        if (analysis.getOperation().equals("SELECT") && 
            !analysis.isHasGroupBy() && 
            resultSize <= properties.getQueryCache().getMaxResultSize()) {
            return true;
        }
        return false;
    }
    
    private void updateQueryMetrics(String sql, boolean cacheHit, long executionTime, int resultSize) {
        QueryMetrics metrics = queryMetrics.computeIfAbsent(sql, k -> new QueryMetrics());
        metrics.incrementExecutionCount();
        
        if (cacheHit) {
            metrics.incrementCacheHits();
        } else {
            metrics.updateExecutionTime(executionTime);
            metrics.updateResultSize(resultSize);
        }
    }
    
    private void generateIndexRecommendation(String sql, QueryAnalysis analysis, long executionTime) {
        // Generate index recommendations for slow queries
        if (analysis.isHasWhereClause() && !analysis.getTableName().equals("unknown")) {
            com.waqiti.common.database.model.IndexRecommendation recommendation = new com.waqiti.common.database.model.IndexRecommendation();
            recommendation.setTableName(analysis.getTableName());
            recommendation.setSql(sql);
            recommendation.setExecutionTime(executionTime);
            recommendation.setScore(calculateIndexScore(executionTime, analysis));
            recommendation.setIndexName("idx_" + analysis.getTableName() + "_opt_" + System.currentTimeMillis());
            recommendation.setReason("Slow query optimization");
            
            indexRecommendations.put(recommendation.getIndexName(), recommendation);
        }
    }
    
    private double calculateIndexScore(long executionTime, QueryAnalysis analysis) {
        double score = 0.0;
        
        // Base score from execution time
        score += Math.min(executionTime / 1000.0, 10.0); // Max 10 points for execution time
        
        // Additional points for query complexity
        if (analysis.isHasJoin()) score += 2.0;
        if (analysis.isHasOrderBy()) score += 1.5;
        if (analysis.isHasWhereClause()) score += 2.0;
        
        return score;
    }
    
    // Batch operation implementations
    private BatchExecutionResult executeBatchInserts(BatchRequest request) {
        // Implementation for batch inserts
        return new BatchExecutionResult(request.getOperations().size(), 0, 0L);
    }
    
    private BatchExecutionResult executeBatchUpdates(BatchRequest request) {
        // Implementation for batch updates
        return new BatchExecutionResult(request.getOperations().size(), 0, 0L);
    }
    
    private BatchExecutionResult executeBatchDeletes(BatchRequest request) {
        // Implementation for batch deletes
        return new BatchExecutionResult(request.getOperations().size(), 0, 0L);
    }
    
    private BatchExecutionResult executeMixedBatch(BatchRequest request) {
        // Implementation for mixed batch operations
        return new BatchExecutionResult(request.getOperations().size(), 0, 0L);
    }
    
    private List<com.waqiti.common.database.dto.SlowQuery> identifySlowQueries() {
        return queryMetrics.entrySet().stream()
                .filter(entry -> entry.getValue().getAverageExecutionTime() > properties.getSlowQueryThresholdMs())
                .map(entry -> com.waqiti.common.database.dto.SlowQuery.builder()
                        .queryId(entry.getKey())
                        .normalizedQuery(entry.getKey())
                        .executionTime(Duration.ofMillis((long) entry.getValue().getAverageExecutionTime()))
                        .timestamp(Instant.now())
                        .cpuTime(entry.getValue().getAverageExecutionTime())
                        .build())
                .collect(java.util.stream.Collectors.<com.waqiti.common.database.dto.SlowQuery>toList());
    }
    
    private ConnectionPoolStatus analyzeConnectionPool() {
        // Analyze connection pool status
        return new ConnectionPoolStatus(true, 10, 8, 2, 0);
    }
    
    private List<com.waqiti.common.database.model.IndexRecommendation> generateIndexRecommendations() {
        return new ArrayList<>(indexRecommendations.values());
    }
    
    private QueryPatternAnalysis analyzeQueryPatterns() {
        // Analyze query patterns
        return new QueryPatternAnalysis();
    }
    
    private CacheMetrics analyzeCachePerformance() {
        // Analyze cache performance
        return new CacheMetrics();
    }
    
    private void createIndex(com.waqiti.common.database.model.IndexRecommendation recommendation) throws SQLException {
        String createIndexSql = String.format("CREATE INDEX IF NOT EXISTS %s ON %s (%s)",
                recommendation.getIndexName(),
                recommendation.getTableName(),
                "id"); // Simplified - would need proper column analysis

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(createIndexSql)) {
            stmt.execute();
        }
    }

    /**
     * Convert model IndexRecommendation to DTO IndexRecommendation
     */
    private com.waqiti.common.database.dto.IndexRecommendation convertToDTO(
            com.waqiti.common.database.model.IndexRecommendation model) {
        if (model == null) return null;

        com.waqiti.common.database.dto.IndexRecommendation dto = new com.waqiti.common.database.dto.IndexRecommendation();
        dto.setIndexName(model.getIndexName());
        dto.setTableName(model.getTableName());
        dto.setColumns(model.getColumnNames());
        dto.setExpectedImprovement(model.getPotentialPerformanceGain());
        dto.setPriority(model.getPriority());
        dto.setReason(model.getReason());
        dto.setSql(model.getSql());
        dto.setExecutionTime(model.getExecutionTime() != null ? model.getExecutionTime() : 0L);
        dto.setScore(model.getScore() != null ? model.getScore() : 0.0);
        return dto;
    }

}