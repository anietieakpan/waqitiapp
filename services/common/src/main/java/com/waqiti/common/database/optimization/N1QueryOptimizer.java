package com.waqiti.common.database.optimization;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOP-based N+1 query detection and optimization
 */
@Aspect
@Component
@Slf4j
public class N1QueryOptimizer {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final ConcurrentHashMap<String, QueryStatistics> queryStats = new ConcurrentHashMap<>();
    
    /**
     * Monitors repository method executions for N+1 patterns
     */
    @Around("@within(org.springframework.stereotype.Repository)")
    public Object monitorRepositoryQueries(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodKey = joinPoint.getSignature().toShortString();
        Session session = entityManager.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        
        long queryCountBefore = stats.getQueryExecutionCount();
        long entityLoadCountBefore = stats.getEntityLoadCount();
        long collectionLoadCountBefore = stats.getCollectionLoadCount();
        
        try {
            Object result = joinPoint.proceed();
            
            long queryCountAfter = stats.getQueryExecutionCount();
            long entityLoadCountAfter = stats.getEntityLoadCount();
            long collectionLoadCountAfter = stats.getCollectionLoadCount();
            
            long queryCount = queryCountAfter - queryCountBefore;
            long entityLoadCount = entityLoadCountAfter - entityLoadCountBefore;
            long collectionLoadCount = collectionLoadCountAfter - collectionLoadCountBefore;
            
            // Detect potential N+1 queries
            if (queryCount > 1 && (entityLoadCount > queryCount || collectionLoadCount > 0)) {
                log.warn("Potential N+1 query detected in {}: {} queries, {} entity loads, {} collection loads",
                    methodKey, queryCount, entityLoadCount, collectionLoadCount);
                
                // Track statistics
                queryStats.computeIfAbsent(methodKey, k -> new QueryStatistics())
                    .recordExecution(queryCount, entityLoadCount, collectionLoadCount);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error monitoring queries for method: {}", methodKey, e);
            throw e;
        }
    }
    
    /**
     * Get query statistics for analysis
     */
    public ConcurrentHashMap<String, QueryStatistics> getQueryStatistics() {
        return new ConcurrentHashMap<String, QueryStatistics>(queryStats);
    }
    
    /**
     * Clear query statistics
     */
    public void clearStatistics() {
        queryStats.clear();
    }
    
    /**
     * Inner class to track query statistics
     */
    public static class QueryStatistics {
        private final AtomicLong executionCount = new AtomicLong(0);
        private final AtomicLong totalQueries = new AtomicLong(0);
        private final AtomicLong totalEntityLoads = new AtomicLong(0);
        private final AtomicLong totalCollectionLoads = new AtomicLong(0);
        
        public void recordExecution(long queries, long entityLoads, long collectionLoads) {
            executionCount.incrementAndGet();
            totalQueries.addAndGet(queries);
            totalEntityLoads.addAndGet(entityLoads);
            totalCollectionLoads.addAndGet(collectionLoads);
        }
        
        public double getAverageQueriesPerExecution() {
            long count = executionCount.get();
            return count > 0 ? (double) totalQueries.get() / count : 0;
        }
        
        public double getAverageEntityLoadsPerExecution() {
            long count = executionCount.get();
            return count > 0 ? (double) totalEntityLoads.get() / count : 0;
        }
        
        public double getAverageCollectionLoadsPerExecution() {
            long count = executionCount.get();
            return count > 0 ? (double) totalCollectionLoads.get() / count : 0;
        }
        
        public boolean isLikelyN1Problem() {
            return getAverageQueriesPerExecution() > 2 && 
                   (getAverageEntityLoadsPerExecution() > getAverageQueriesPerExecution() ||
                    getAverageCollectionLoadsPerExecution() > 0);
        }
    }
}