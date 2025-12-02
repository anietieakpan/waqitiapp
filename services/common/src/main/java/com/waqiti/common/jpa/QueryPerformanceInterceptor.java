package com.waqiti.common.jpa;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Query Performance Interceptor
 * Monitors and logs query performance to detect N+1 queries and performance issues
 */
@Slf4j
@Component
public class QueryPerformanceInterceptor implements StatementInspector {

    private final ThreadLocal<QueryContext> queryContext = new ThreadLocal<>();
    private final ConcurrentHashMap<String, QueryStats> queryStatsMap = new ConcurrentHashMap<>();
    
    private static final int N_PLUS_ONE_THRESHOLD = 10; // Number of similar queries to trigger N+1 warning
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000; // 1 second
    
    @Override
    public String inspect(String sql) {
        long startTime = System.currentTimeMillis();
        
        // Initialize query context if not exists
        if (queryContext.get() == null) {
            queryContext.set(new QueryContext());
        }
        
        QueryContext context = queryContext.get();
        context.incrementQueryCount();
        
        // Detect potential N+1 queries
        String normalizedSql = normalizeSql(sql);
        context.addQuery(normalizedSql);
        
        // Check for N+1 pattern
        if (context.getQueryCount(normalizedSql) > N_PLUS_ONE_THRESHOLD) {
            log.warn("Potential N+1 query detected: {} (executed {} times in this context)", 
                normalizedSql, context.getQueryCount(normalizedSql));
        }
        
        // Track query statistics
        updateQueryStats(normalizedSql, startTime);
        
        return sql;
    }
    
    private String normalizeSql(String sql) {
        return sql.replaceAll("\\d+", "?")  // Replace numbers with placeholders
                  .replaceAll("'[^']*'", "?")  // Replace string literals
                  .replaceAll("\\s+", " ")     // Normalize whitespace
                  .trim()
                  .toLowerCase();
    }
    
    private void updateQueryStats(String normalizedSql, long startTime) {
        QueryStats stats = queryStatsMap.computeIfAbsent(normalizedSql, k -> new QueryStats());
        stats.incrementCount();
        
        long executionTime = System.currentTimeMillis() - startTime;
        stats.addExecutionTime(executionTime);
        
        if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("Slow query detected ({}ms): {}", executionTime, normalizedSql);
        }
    }
    
    public void clearContext() {
        queryContext.remove();
    }
    
    public QueryContext getCurrentContext() {
        return queryContext.get();
    }
    
    public ConcurrentHashMap<String, QueryStats> getQueryStats() {
        return new ConcurrentHashMap<>(queryStatsMap);
    }
    
    public void resetStats() {
        queryStatsMap.clear();
    }
    
    /**
     * Query execution context for a single request/transaction
     */
    public static class QueryContext {
        private final ConcurrentHashMap<String, AtomicInteger> queryCountMap = new ConcurrentHashMap<>();
        private final AtomicInteger totalQueries = new AtomicInteger(0);
        private final long startTime = System.currentTimeMillis();
        
        public void addQuery(String normalizedSql) {
            queryCountMap.computeIfAbsent(normalizedSql, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        public int getQueryCount(String normalizedSql) {
            AtomicInteger count = queryCountMap.get(normalizedSql);
            return count != null ? count.get() : 0;
        }
        
        public void incrementQueryCount() {
            totalQueries.incrementAndGet();
        }
        
        public int getTotalQueries() {
            return totalQueries.get();
        }
        
        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }
        
        public boolean hasNPlusOneQueries() {
            return queryCountMap.values().stream()
                .anyMatch(count -> count.get() > N_PLUS_ONE_THRESHOLD);
        }
    }
    
    /**
     * Query statistics for monitoring
     */
    public static class QueryStats {
        private final AtomicLong executionCount = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicLong maxExecutionTime = new AtomicLong(0);
        private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
        
        public void incrementCount() {
            executionCount.incrementAndGet();
        }
        
        public void addExecutionTime(long time) {
            totalExecutionTime.addAndGet(time);
            maxExecutionTime.updateAndGet(max -> Math.max(max, time));
            minExecutionTime.updateAndGet(min -> Math.min(min, time));
        }
        
        public long getExecutionCount() {
            return executionCount.get();
        }
        
        public double getAverageExecutionTime() {
            long count = executionCount.get();
            return count > 0 ? (double) totalExecutionTime.get() / count : 0;
        }
        
        public long getMaxExecutionTime() {
            return maxExecutionTime.get();
        }
        
        public long getMinExecutionTime() {
            long min = minExecutionTime.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        
        public long getTotalExecutionTime() {
            return totalExecutionTime.get();
        }
    }
}