package com.waqiti.common.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects optimal database connection pool based on query characteristics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionSelector {
    private final QueryPerformancePredictor performancePredictor;
    private final Map<String, PoolSelectionStrategy> strategies = new ConcurrentHashMap<>();
    
    public String selectOptimalPool(String queryPattern, QueryPriority priority) {
        log.debug("Selecting optimal pool for query pattern: {} with priority: {}", queryPattern, priority);
        
        PoolSelectionStrategy strategy = strategies.getOrDefault(priority.name(), getDefaultStrategy());
        String selectedPool = strategy.selectPool(queryPattern, priority);
        
        log.debug("Selected pool: {} for query pattern: {}", selectedPool, queryPattern);
        return selectedPool;
    }
    
    public void registerStrategy(String priorityLevel, PoolSelectionStrategy strategy) {
        strategies.put(priorityLevel, strategy);
        log.info("Registered pool selection strategy for priority: {}", priorityLevel);
    }
    
    private PoolSelectionStrategy getDefaultStrategy() {
        return new LoadBalancedPoolSelectionStrategy();
    }
    
    public interface PoolSelectionStrategy {
        String selectPool(String queryPattern, QueryPriority priority);
    }
    
    public static class LoadBalancedPoolSelectionStrategy implements PoolSelectionStrategy {
        private final List<String> availablePools = List.of("read-replica", "primary", "analytics");
        private int currentIndex = 0;
        
        @Override
        public String selectPool(String queryPattern, QueryPriority priority) {
            switch (priority) {
                case HIGH:
                    return "primary";
                case MEDIUM:
                    return queryPattern.contains("SELECT") ? "read-replica" : "primary";
                case LOW:
                    return queryPattern.contains("SELECT") ? "analytics" : "primary";
                default:
                    // Round-robin for unknown priority
                    String pool = availablePools.get(currentIndex % availablePools.size());
                    currentIndex++;
                    return pool;
            }
        }
    }
    
    public static class PerformanceBasedPoolSelectionStrategy implements PoolSelectionStrategy {
        private final QueryPerformancePredictor predictor;
        
        public PerformanceBasedPoolSelectionStrategy(QueryPerformancePredictor predictor) {
            this.predictor = predictor;
        }
        
        @Override
        public String selectPool(String queryPattern, QueryPriority priority) {
            QueryPerformanceMetrics metrics = predictor.getMetrics(queryPattern);
            
            if (metrics.getAverageExecutionTimeMs() > 1000) {
                return "analytics"; // Long-running queries go to analytics pool
            } else if (priority == QueryPriority.HIGH) {
                return "primary";
            } else {
                return "read-replica";
            }
        }
    }
}