package com.waqiti.common.database;

/**
 * Priority levels for database queries
 */
public enum QueryPriority {
    /**
     * Critical queries that must execute immediately (e.g., payment processing)
     */
    CRITICAL(1),
    
    /**
     * High priority queries for real-time operations (e.g., balance checks)
     */
    HIGH(2),
    
    /**
     * Medium priority queries for normal operations (e.g., transaction history)
     */
    MEDIUM(3),
    
    /**
     * Low priority queries for background operations (e.g., analytics, reports)
     */
    LOW(4),
    
    /**
     * Background queries that can be delayed (e.g., data cleanup, migrations)
     */
    BACKGROUND(5);
    
    private final int level;
    
    QueryPriority(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
    
    public boolean isHigherPriorityThan(QueryPriority other) {
        return this.level < other.level;
    }
    
    public boolean isLowerPriorityThan(QueryPriority other) {
        return this.level > other.level;
    }
    
    public static QueryPriority fromLevel(int level) {
        for (QueryPriority priority : values()) {
            if (priority.level == level) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Invalid priority level: " + level);
    }
    
    public static QueryPriority fromString(String priorityStr) {
        if (priorityStr == null || priorityStr.trim().isEmpty()) {
            return MEDIUM; // Default priority
        }
        
        try {
            return valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM; // Default priority for invalid strings
        }
    }
    
    /**
     * Determines priority based on query type
     */
    public static QueryPriority fromQueryType(String query) {
        if (query == null) {
            return MEDIUM;
        }
        
        String upperQuery = query.toUpperCase().trim();
        
        // Critical operations
        if (upperQuery.contains("TRANSFER") || upperQuery.contains("PAYMENT") || 
            upperQuery.contains("BALANCE") && upperQuery.contains("UPDATE")) {
            return CRITICAL;
        }
        
        // High priority operations
        if (upperQuery.startsWith("SELECT") && 
            (upperQuery.contains("BALANCE") || upperQuery.contains("ACCOUNT"))) {
            return HIGH;
        }
        
        // Medium priority operations
        if (upperQuery.startsWith("SELECT") || upperQuery.startsWith("INSERT") || 
            upperQuery.startsWith("UPDATE")) {
            return MEDIUM;
        }
        
        // Low priority operations
        if (upperQuery.contains("REPORT") || upperQuery.contains("ANALYTICS") || 
            upperQuery.contains("AGGREGATE")) {
            return LOW;
        }
        
        // Background operations
        if (upperQuery.contains("CLEANUP") || upperQuery.contains("MIGRATION") || 
            upperQuery.startsWith("DELETE")) {
            return BACKGROUND;
        }
        
        return MEDIUM; // Default
    }
}