package com.waqiti.common.performance;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Statistics about database indexes
 */
@Data
public class IndexStatistics {
    
    private final Map<String, Integer> tableIndexCounts = new HashMap<>();
    private final Map<String, Integer> tableRowCounts = new HashMap<>();
    
    public void addTableIndexCount(String table, int count) {
        tableIndexCounts.put(table, count);
    }
    
    public void addTableRowCount(String table, int count) {
        tableRowCounts.put(table, count);
    }
    
    public int getTotalIndexCount() {
        return tableIndexCounts.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
    }
    
    public int getTotalRowCount() {
        return tableRowCounts.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
    }
    
    public double getAverageIndexesPerTable() {
        if (tableIndexCounts.isEmpty()) return 0;
        return (double) getTotalIndexCount() / tableIndexCounts.size();
    }
    
    public String getLargestTable() {
        return tableRowCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("N/A");
    }
    
    public int getLargestTableRowCount() {
        return tableRowCounts.values().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
    }
}