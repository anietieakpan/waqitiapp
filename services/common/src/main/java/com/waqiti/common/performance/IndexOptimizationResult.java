package com.waqiti.common.performance;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of index optimization process
 */
@Data
public class IndexOptimizationResult {
    
    private final List<String> createdIndexes = new ArrayList<>();
    private final List<String> existingIndexes = new ArrayList<>();
    private final List<String> failedIndexes = new ArrayList<>();
    
    public void addCreated(String indexName) {
        createdIndexes.add(indexName);
    }
    
    public void addExisting(String indexName) {
        existingIndexes.add(indexName);
    }
    
    public void addFailed(String indexName) {
        failedIndexes.add(indexName);
    }
    
    public int getCreatedCount() {
        return createdIndexes.size();
    }
    
    public int getExistingCount() {
        return existingIndexes.size();
    }
    
    public int getFailedCount() {
        return failedIndexes.size();
    }
    
    public int getTotalProcessed() {
        return getCreatedCount() + getExistingCount() + getFailedCount();
    }
    
    public boolean hasFailures() {
        return !failedIndexes.isEmpty();
    }
    
    public double getSuccessRate() {
        if (getTotalProcessed() == 0) return 100.0;
        return ((double) (getCreatedCount() + getExistingCount()) / getTotalProcessed()) * 100;
    }
    
    public String getSummary() {
        return String.format("Index Optimization: %d created, %d existing, %d failed (%.1f%% success rate)",
            getCreatedCount(), getExistingCount(), getFailedCount(), getSuccessRate());
    }
}