package com.waqiti.common.database.performance.models;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Event data for database deadlock tracking.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class DeadlockEvent {
    
    private String tableName;
    private String lockType;
    private String query1;
    private String query2;
    private List<String> affectedTables;
    private String resolution;
    private long resolutionTimeMs;
    private Instant timestamp;
    private String sessionId1;
    private String sessionId2;
    private String application1;
    private String application2;
    private int severity; // 1-5 scale
    
    /**
     * Determine the severity of this deadlock based on impact.
     *
     * @return severity level from 1 (low) to 5 (critical)
     */
    public int calculateSeverity() {
        int severity = 1;
        
        // Increase severity based on resolution time
        if (resolutionTimeMs > 10000) { // > 10 seconds
            severity += 2;
        } else if (resolutionTimeMs > 5000) { // > 5 seconds
            severity += 1;
        }
        
        // Increase severity based on number of affected tables
        if (affectedTables != null && affectedTables.size() > 3) {
            severity += 1;
        }
        
        // Critical tables increase severity
        if (tableName != null && isCriticalTable(tableName)) {
            severity += 1;
        }
        
        return Math.min(5, severity);
    }
    
    /**
     * Generate a human-readable description of the deadlock.
     *
     * @return deadlock description
     */
    public String getDescription() {
        return String.format(
            "Deadlock on table %s between sessions %s and %s. " +
            "Lock type: %s. Resolution time: %dms. Severity: %d",
            tableName, sessionId1, sessionId2, lockType, resolutionTimeMs, calculateSeverity()
        );
    }
    
    private boolean isCriticalTable(String table) {
        // Define critical tables that should trigger higher severity
        return table.contains("payment") || 
               table.contains("transaction") || 
               table.contains("account") ||
               table.contains("user") ||
               table.contains("balance");
    }
}