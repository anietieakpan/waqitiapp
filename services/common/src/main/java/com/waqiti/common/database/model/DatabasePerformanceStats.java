package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive database performance statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabasePerformanceStats {
    
    public static class DatabasePerformanceStatsBuilder {
        public DatabasePerformanceStatsBuilder databaseProductName(String productName) {
            // Store as database name
            this.databaseName = productName;
            return this;
        }
        
        public DatabasePerformanceStatsBuilder databaseVersion(String version) {
            // Store version in metadata for now
            return this;
        }
        
        public DatabasePerformanceStatsBuilder generatedAt(java.time.LocalDateTime generatedAt) {
            this.timestamp = generatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant();
            return this;
        }
        
        public DatabasePerformanceStatsBuilder indexStats(java.util.List<IndexUsageStats> indexStats) {
            this.indexUsageStats = indexStats;
            return this;
        }
        
        public DatabasePerformanceStatsBuilder error(String error) {
            this.error = error;
            return this;
        }
    }
    
    /**
     * Stats ID
     */
    private String statsId;
    
    /**
     * Collection timestamp
     */
    private Instant timestamp;
    
    /**
     * Database name
     */
    private String databaseName;
    
    /**
     * Connection pool stats
     */
    private ConnectionPoolStats connectionPoolStats;
    
    /**
     * Query performance stats
     */
    private QueryPerformanceStats queryPerformanceStats;
    
    /**
     * Table statistics
     */
    private List<TableStats> tableStats;
    
    /**
     * Index statistics
     */
    private List<IndexUsageStats> indexUsageStats;
    
    /**
     * Cache statistics
     */
    private CacheStats cacheStats;
    
    /**
     * Lock statistics
     */
    private LockStats lockStats;
    
    /**
     * I/O statistics
     */
    private IOStats ioStats;
    
    /**
     * Replication stats
     */
    private ReplicationStats replicationStats;
    
    /**
     * Resource usage
     */
    private ResourceUsage resourceUsage;
    
    /**
     * Alerts and warnings
     */
    private List<PerformanceAlert> alerts;
    
    /**
     * Error message if stats collection failed
     */
    private String error;
}