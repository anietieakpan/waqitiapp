package com.waqiti.common.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade Query Bus implementation for CQRS pattern
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryBus {

    private final ApplicationContext applicationContext;
    private final Map<Class<?>, QueryHandler<?, ?>> handlers = new ConcurrentHashMap<>();
    private final Map<String, QueryMetrics> metrics = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Auto-register all query handlers
        Map<String, QueryHandler> handlerBeans = applicationContext.getBeansOfType(QueryHandler.class);
        handlerBeans.values().forEach(this::registerHandler);
        log.info("Registered {} query handlers", handlers.size());
    }

    /**
     * Execute a query synchronously
     */
    @SuppressWarnings("unchecked")
    public <R> R execute(Query<R> query) {
        Class<?> queryClass = query.getClass();
        QueryHandler<Query<R>, R> handler = (QueryHandler<Query<R>, R>) handlers.get(queryClass);
        
        if (handler == null) {
            throw new QueryHandlerNotFoundException("No handler found for query: " + queryClass.getName());
        }
        
        long startTime = System.currentTimeMillis();
        try {
            log.debug("Executing query: {}", queryClass.getSimpleName());
            R result = handler.handle(query);
            recordMetrics(queryClass.getName(), System.currentTimeMillis() - startTime, true);
            return result;
        } catch (Exception e) {
            recordMetrics(queryClass.getName(), System.currentTimeMillis() - startTime, false);
            log.error("Error executing query: {}", queryClass.getName(), e);
            throw new QueryExecutionException("Failed to execute query: " + queryClass.getName(), e);
        }
    }

    /**
     * Execute a query asynchronously
     */
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> executeAsync(Query<R> query) {
        return CompletableFuture.supplyAsync(() -> execute(query));
    }

    /**
     * Register a query handler
     */
    public <Q extends Query<R>, R> void registerHandler(QueryHandler<Q, R> handler) {
        Class<?> queryClass = getQueryClass(handler);
        handlers.put(queryClass, handler);
        log.debug("Registered handler for query: {}", queryClass.getName());
    }

    /**
     * Unregister a query handler
     */
    public void unregisterHandler(Class<? extends Query<?>> queryClass) {
        handlers.remove(queryClass);
        log.debug("Unregistered handler for query: {}", queryClass.getName());
    }

    /**
     * Check if handler exists for query
     */
    public boolean hasHandler(Class<? extends Query<?>> queryClass) {
        return handlers.containsKey(queryClass);
    }

    /**
     * Get the query class from handler
     */
    @SuppressWarnings("unchecked")
    private Class<?> getQueryClass(QueryHandler<?, ?> handler) {
        Type[] interfaces = handler.getClass().getGenericInterfaces();
        for (Type type : interfaces) {
            if (type instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) type;
                if (QueryHandler.class.isAssignableFrom((Class<?>) paramType.getRawType())) {
                    return (Class<?>) paramType.getActualTypeArguments()[0];
                }
            }
        }
        
        // Check superclass
        Type superclass = handler.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) superclass;
            return (Class<?>) paramType.getActualTypeArguments()[0];
        }
        
        throw new IllegalArgumentException("Cannot determine query type for handler: " + handler.getClass());
    }

    /**
     * Record query metrics
     */
    private void recordMetrics(String queryName, long executionTime, boolean success) {
        metrics.compute(queryName, (key, existing) -> {
            if (existing == null) {
                existing = new QueryMetrics(queryName);
            }
            existing.record(executionTime, success);
            return existing;
        });
    }

    /**
     * Get metrics for a query
     */
    public QueryMetrics getMetrics(String queryName) {
        return metrics.get(queryName);
    }

    /**
     * Get all metrics
     */
    public Map<String, QueryMetrics> getAllMetrics() {
        return new HashMap<>(metrics);
    }

    /**
     * Clear metrics
     */
    public void clearMetrics() {
        metrics.clear();
    }

    /**
     * Query metrics class
     */
    public static class QueryMetrics {
        private final String queryName;
        private long totalExecutions = 0;
        private long successfulExecutions = 0;
        private long failedExecutions = 0;
        private long totalExecutionTime = 0;
        private long minExecutionTime = Long.MAX_VALUE;
        private long maxExecutionTime = 0;

        public QueryMetrics(String queryName) {
            this.queryName = queryName;
        }

        public synchronized void record(long executionTime, boolean success) {
            totalExecutions++;
            totalExecutionTime += executionTime;
            minExecutionTime = Math.min(minExecutionTime, executionTime);
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
        }

        public double getAverageExecutionTime() {
            return totalExecutions > 0 ? (double) totalExecutionTime / totalExecutions : 0;
        }

        public double getSuccessRate() {
            return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions * 100 : 0;
        }

        // Getters
        public String getQueryName() { return queryName; }
        public long getTotalExecutions() { return totalExecutions; }
        public long getSuccessfulExecutions() { return successfulExecutions; }
        public long getFailedExecutions() { return failedExecutions; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public long getMinExecutionTime() { return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
    }
}