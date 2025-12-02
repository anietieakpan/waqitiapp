package com.waqiti.common.database;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;

/**
 * Collects and exposes database metrics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicInteger> activeQueriesByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> queryTimers = new ConcurrentHashMap<>();
    
    public void recordQueryExecution(String queryType, long executionTimeMs) {
        Timer timer = queryTimers.computeIfAbsent(queryType, 
            type -> Timer.builder("database.query.execution")
                .tag("type", type)
                .description("Query execution time")
                .register(meterRegistry));
        
        timer.record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordConnectionAcquired(String poolName) {
        Counter.builder("database.connection.acquired")
            .tag("pool", poolName)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordConnectionReleased(String poolName) {
        Counter.builder("database.connection.released")
            .tag("pool", poolName)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordQueryError(String queryType, String errorType) {
        Counter.builder("database.query.errors")
            .tag("type", queryType)
            .tag("error", errorType)
            .register(meterRegistry)
            .increment();
    }
    
    public void incrementActiveQueries(String queryType) {
        activeQueriesByType.computeIfAbsent(queryType, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder("database.queries.active", gauge, AtomicInteger::get)
                .tag("type", queryType)
                .register(meterRegistry);
            return gauge;
        }).incrementAndGet();
    }
    
    public void decrementActiveQueries(String queryType) {
        AtomicInteger counter = activeQueriesByType.get(queryType);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }
    
    public void recordCacheHit(String cacheType) {
        Counter.builder("database.cache.hits")
            .tag("type", cacheType)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordCacheMiss(String cacheType) {
        Counter.builder("database.cache.misses")
            .tag("type", cacheType)
            .register(meterRegistry)
            .increment();
    }
    
    public void registerDataSourceMetrics(DataSource dataSource, String name) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            Gauge.builder("database.connections.max", metaData, 
                md -> {
                    try {
                        return md.getMaxConnections();
                    } catch (SQLException e) {
                        return -1;
                    }
                })
                .tag("datasource", name)
                .register(meterRegistry);
                
        } catch (SQLException e) {
            log.error("Failed to register datasource metrics for {}", name, e);
        }
    }
    
    public List<PredictedQuery> getRecentQueries(int limit) {
        // This would typically query a database or cache for recent query patterns
        // For now, return some mock data
        List<PredictedQuery> recentQueries = new ArrayList<>();
        
        for (int i = 0; i < Math.min(limit, 10); i++) {
            PredictedQuery query = PredictedQuery.builder()
                .queryPattern("SELECT * FROM transactions WHERE user_id = ?")
                .probability(0.8 - (i * 0.05))
                .predictedExecutionTime(Instant.now().plusSeconds(i * 60))
                .resourceRequirements(ResourceRequirements.createDefault())
                .estimatedDurationMs(100L + (i * 50))
                .build();
            recentQueries.add(query);
        }
        
        return recentQueries;
    }
    
    public DatabaseResourceRequirements getCurrentResourceUsage() {
        // Get current database resource usage
        return DatabaseResourceRequirements.builder()
            .cpuCores(4) // Mock current CPU cores
            .memoryGb(16) // Mock current memory
            .storageIops(1000) // Mock current IOPS
            .connectionCount(50) // Mock current connections
            .build();
    }
}