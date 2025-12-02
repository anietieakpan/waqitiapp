package com.waqiti.common.query;

/**
 * Marker interface for queries in CQRS pattern
 * @param <R> The result type of the query
 */
public interface Query<R> {
    
    /**
     * Get query name for logging and metrics
     */
    default String getQueryName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Get query priority (higher number = higher priority)
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Check if query can be cached
     */
    default boolean isCacheable() {
        return false;
    }
    
    /**
     * Get cache key for this query
     */
    default String getCacheKey() {
        return getQueryName() + ":" + hashCode();
    }
    
    /**
     * Get cache TTL in seconds
     */
    default long getCacheTTL() {
        return 300; // 5 minutes default
    }
}