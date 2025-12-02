package com.waqiti.common.database.advanced;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.Min;

/**
 * Configuration properties for advanced database optimization.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "waqiti.database.optimization")
public class DatabaseOptimizationProperties {
    
    /**
     * Whether database optimization is enabled
     */
    private boolean enabled = true;
    
    /**
     * Whether automatic optimization is enabled
     */
    private boolean autoOptimizationEnabled = false;
    
    /**
     * Whether automatic index optimization is enabled
     */
    private boolean autoIndexOptimizationEnabled = false;
    
    /**
     * Slow query threshold in milliseconds
     */
    @Min(value = 100, message = "Slow query threshold must be at least 100ms")
    private long slowQueryThresholdMs = 1000L;
    
    /**
     * Default query limit for SELECT queries without LIMIT clause
     */
    @Min(value = 1, message = "Default query limit must be at least 1")
    private int defaultQueryLimit = 1000;
    
    /**
     * Optimization interval in minutes
     */
    @Min(value = 1, message = "Optimization interval must be at least 1 minute")
    private long optimizationIntervalMinutes = 60;
    
    /**
     * Threshold score for automatic index creation
     */
    private double autoIndexCreationThreshold = 7.5;
    
    /**
     * Query cache configuration
     */
    private QueryCacheConfig queryCache = new QueryCacheConfig();
    
    /**
     * Connection pool optimization configuration
     */
    private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();
    
    /**
     * Batch processing configuration
     */
    private BatchConfig batch = new BatchConfig();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public boolean isAutoOptimizationEnabled() { return autoOptimizationEnabled; }
    public void setAutoOptimizationEnabled(boolean autoOptimizationEnabled) { this.autoOptimizationEnabled = autoOptimizationEnabled; }
    
    public boolean isAutoIndexOptimizationEnabled() { return autoIndexOptimizationEnabled; }
    public void setAutoIndexOptimizationEnabled(boolean autoIndexOptimizationEnabled) { this.autoIndexOptimizationEnabled = autoIndexOptimizationEnabled; }
    
    public long getSlowQueryThresholdMs() { return slowQueryThresholdMs; }
    public void setSlowQueryThresholdMs(long slowQueryThresholdMs) { this.slowQueryThresholdMs = slowQueryThresholdMs; }
    
    public int getDefaultQueryLimit() { return defaultQueryLimit; }
    public void setDefaultQueryLimit(int defaultQueryLimit) { this.defaultQueryLimit = defaultQueryLimit; }
    
    public long getOptimizationIntervalMinutes() { return optimizationIntervalMinutes; }
    public void setOptimizationIntervalMinutes(long optimizationIntervalMinutes) { this.optimizationIntervalMinutes = optimizationIntervalMinutes; }
    
    public double getAutoIndexCreationThreshold() { return autoIndexCreationThreshold; }
    public void setAutoIndexCreationThreshold(double autoIndexCreationThreshold) { this.autoIndexCreationThreshold = autoIndexCreationThreshold; }
    
    public QueryCacheConfig getQueryCache() { return queryCache; }
    public void setQueryCache(QueryCacheConfig queryCache) { this.queryCache = queryCache; }
    
    public ConnectionPoolConfig getConnectionPool() { return connectionPool; }
    public void setConnectionPool(ConnectionPoolConfig connectionPool) { this.connectionPool = connectionPool; }
    
    public BatchConfig getBatch() { return batch; }
    public void setBatch(BatchConfig batch) { this.batch = batch; }
    
    /**
     * Configuration for query result caching.
     */
    public static class QueryCacheConfig {
        
        /**
         * Whether query result caching is enabled
         */
        private boolean enabled = true;
        
        /**
         * Redis key prefix for cached queries
         */
        private String keyPrefix = "waqiti:db:cache:";
        
        /**
         * Default TTL for cached results in minutes
         */
        @Min(value = 1, message = "Default TTL must be at least 1 minute")
        private long defaultTtlMinutes = 30;
        
        /**
         * Maximum result size to cache (number of rows)
         */
        @Min(value = 1, message = "Max result size must be at least 1")
        private int maxResultSize = 1000;
        
        /**
         * Maximum cache entry size in bytes
         */
        private long maxEntrySizeBytes = 1024 * 1024; // 1MB
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
        
        public long getDefaultTtlMinutes() { return defaultTtlMinutes; }
        public void setDefaultTtlMinutes(long defaultTtlMinutes) { this.defaultTtlMinutes = defaultTtlMinutes; }
        
        public int getMaxResultSize() { return maxResultSize; }
        public void setMaxResultSize(int maxResultSize) { this.maxResultSize = maxResultSize; }
        
        public long getMaxEntrySizeBytes() { return maxEntrySizeBytes; }
        public void setMaxEntrySizeBytes(long maxEntrySizeBytes) { this.maxEntrySizeBytes = maxEntrySizeBytes; }
    }
    
    /**
     * Configuration for connection pool optimization.
     */
    public static class ConnectionPoolConfig {
        
        /**
         * Whether connection pool optimization is enabled
         */
        private boolean enabled = true;
        
        /**
         * Minimum idle connections
         */
        @Min(value = 1, message = "Minimum idle connections must be at least 1")
        private int minIdle = 5;
        
        /**
         * Maximum active connections
         */
        @Min(value = 1, message = "Maximum active connections must be at least 1")
        private int maxActive = 20;
        
        /**
         * Maximum wait time for connection in milliseconds
         */
        @Min(value = 1000, message = "Max wait time must be at least 1000ms")
        private long maxWaitMs = 30000L;
        
        /**
         * Whether to test connections on borrow
         */
        private boolean testOnBorrow = true;
        
        /**
         * Whether to test connections on return
         */
        private boolean testOnReturn = false;
        
        /**
         * Whether to test idle connections
         */
        private boolean testWhileIdle = true;
        
        /**
         * Validation query for testing connections
         */
        private String validationQuery = "SELECT 1";
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        
        public int getMaxActive() { return maxActive; }
        public void setMaxActive(int maxActive) { this.maxActive = maxActive; }
        
        public long getMaxWaitMs() { return maxWaitMs; }
        public void setMaxWaitMs(long maxWaitMs) { this.maxWaitMs = maxWaitMs; }
        
        public boolean isTestOnBorrow() { return testOnBorrow; }
        public void setTestOnBorrow(boolean testOnBorrow) { this.testOnBorrow = testOnBorrow; }
        
        public boolean isTestOnReturn() { return testOnReturn; }
        public void setTestOnReturn(boolean testOnReturn) { this.testOnReturn = testOnReturn; }
        
        public boolean isTestWhileIdle() { return testWhileIdle; }
        public void setTestWhileIdle(boolean testWhileIdle) { this.testWhileIdle = testWhileIdle; }
        
        public String getValidationQuery() { return validationQuery; }
        public void setValidationQuery(String validationQuery) { this.validationQuery = validationQuery; }
    }
    
    /**
     * Configuration for batch processing.
     */
    public static class BatchConfig {
        
        /**
         * Whether batch processing optimization is enabled
         */
        private boolean enabled = true;
        
        /**
         * Default batch size for operations
         */
        @Min(value = 1, message = "Default batch size must be at least 1")
        private int defaultBatchSize = 1000;
        
        /**
         * Maximum batch size allowed
         */
        @Min(value = 1, message = "Maximum batch size must be at least 1")
        private int maxBatchSize = 10000;
        
        /**
         * Batch timeout in milliseconds
         */
        @Min(value = 1000, message = "Batch timeout must be at least 1000ms")
        private long batchTimeoutMs = 30000L;
        
        /**
         * Number of parallel batch threads
         */
        @Min(value = 1, message = "Parallel threads must be at least 1")
        private int parallelThreads = 5;
        
        /**
         * Whether to use batch inserts for better performance
         */
        private boolean useBatchInserts = true;
        
        /**
         * Whether to use batch updates for better performance
         */
        private boolean useBatchUpdates = true;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getDefaultBatchSize() { return defaultBatchSize; }
        public void setDefaultBatchSize(int defaultBatchSize) { this.defaultBatchSize = defaultBatchSize; }
        
        public int getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }
        
        public long getBatchTimeoutMs() { return batchTimeoutMs; }
        public void setBatchTimeoutMs(long batchTimeoutMs) { this.batchTimeoutMs = batchTimeoutMs; }
        
        public int getParallelThreads() { return parallelThreads; }
        public void setParallelThreads(int parallelThreads) { this.parallelThreads = parallelThreads; }
        
        public boolean isUseBatchInserts() { return useBatchInserts; }
        public void setUseBatchInserts(boolean useBatchInserts) { this.useBatchInserts = useBatchInserts; }
        
        public boolean isUseBatchUpdates() { return useBatchUpdates; }
        public void setUseBatchUpdates(boolean useBatchUpdates) { this.useBatchUpdates = useBatchUpdates; }
    }
}