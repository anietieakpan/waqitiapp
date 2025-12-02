package com.waqiti.common.database.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Statistics about database index usage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexUsageStats {
    
    private String indexName;
    private String tableName;
    private String schemaName;
    private long totalScans;
    private long totalSeeks;
    private long totalLookups;
    private long totalUpdates;
    private long userScans;
    private long userSeeks;
    private long userLookups;
    private long userUpdates;
    private Instant lastUserScan;
    private Instant lastUserSeek;
    private Instant lastUserLookup;
    private Instant lastUserUpdate;
    private double selectivity;
    private long indexSizeBytes;
    private int indexDepth;
    private boolean isUnique;
    private boolean isPrimaryKey;
    private boolean isClustered;
    private String indexType;
    private long usageCount;
    private double scanRatio;
    private Instant lastUpdated;
    
    /**
     * Calculate total usage count
     */
    public long getTotalUsage() {
        return totalScans + totalSeeks + totalLookups;
    }
    
    /**
     * Calculate user usage count
     */
    public long getUserUsage() {
        return userScans + userSeeks + userLookups;
    }
    
    /**
     * Calculate usage efficiency (user usage vs total usage)
     */
    public double getUsageEfficiency() {
        long total = getTotalUsage();
        if (total == 0) {
            return 0.0;
        }
        return (double) getUserUsage() / total;
    }
    
    /**
     * Check if index is frequently used
     */
    public boolean isFrequentlyUsed() {
        return getUserUsage() > 1000; // Configurable threshold
    }
    
    /**
     * Check if index might be unused
     */
    public boolean isUnused() {
        return getUserUsage() == 0 && totalUpdates > 0;
    }
    
    /**
     * Get last usage timestamp
     */
    public Instant getLastUsage() {
        Instant latest = null;
        
        if (lastUserScan != null) {
            latest = lastUserScan;
        }
        
        if (lastUserSeek != null && (latest == null || lastUserSeek.isAfter(latest))) {
            latest = lastUserSeek;
        }
        
        if (lastUserLookup != null && (latest == null || lastUserLookup.isAfter(latest))) {
            latest = lastUserLookup;
        }
        
        return latest;
    }
}