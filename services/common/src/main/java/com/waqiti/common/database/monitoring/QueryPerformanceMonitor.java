package com.waqiti.common.database.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Monitors query performance and detects potential N+1 queries
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryPerformanceMonitor implements PostLoadEventListener {
    
    private final EntityManagerFactory entityManagerFactory;
    private final MeterRegistry meterRegistry;
    
    private final ConcurrentHashMap<String, QueryMetrics> queryMetrics = new ConcurrentHashMap<>();
    private Counter n1QueryCounter;
    private Timer queryTimer;
    
    @EventListener(ApplicationReadyEvent.class)
    public void registerListeners() {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry()
            .getService(EventListenerRegistry.class);
        
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(this);
        
        // Initialize metrics
        n1QueryCounter = Counter.builder("database.n1.queries")
            .description("Number of potential N+1 queries detected")
            .register(meterRegistry);
            
        queryTimer = Timer.builder("database.query.time")
            .description("Database query execution time")
            .register(meterRegistry);
            
        log.info("Query performance monitor registered");
    }
    
    @Override
    public void onPostLoad(PostLoadEvent event) {
        String entityName = event.getEntity().getClass().getSimpleName();
        
        queryMetrics.compute(entityName, (key, metrics) -> {
            if (metrics == null) {
                metrics = new QueryMetrics();
            }
            metrics.recordLoad();
            
            // Detect potential N+1 pattern
            if (metrics.isN1Pattern()) {
                n1QueryCounter.increment();
                log.warn("Potential N+1 query detected for entity: {} " +
                        "(loaded {} times in {} ms)", 
                        entityName, metrics.loadCount, metrics.getWindowDuration());
            }
            
            return metrics;
        });
    }
    
    /**
     * Get current query metrics
     */
    public ConcurrentHashMap<String, QueryMetrics> getQueryMetrics() {
        return new ConcurrentHashMap<>(queryMetrics);
    }
    
    /**
     * Reset query metrics
     */
    public void resetMetrics() {
        queryMetrics.clear();
    }
    
    /**
     * Inner class to track query metrics
     */
    public static class QueryMetrics {
        private long firstLoadTime;
        private long lastLoadTime;
        private long loadCount;
        private static final long WINDOW_SIZE_MS = 100; // 100ms window
        private static final long N1_THRESHOLD = 10; // More than 10 loads in window
        
        public void recordLoad() {
            long now = System.currentTimeMillis();
            
            if (firstLoadTime == 0) {
                firstLoadTime = now;
            }
            
            // Reset window if too old
            if (now - firstLoadTime > WINDOW_SIZE_MS) {
                firstLoadTime = now;
                loadCount = 0;
            }
            
            lastLoadTime = now;
            loadCount++;
        }
        
        public boolean isN1Pattern() {
            return loadCount > N1_THRESHOLD && getWindowDuration() <= WINDOW_SIZE_MS;
        }
        
        public long getWindowDuration() {
            return lastLoadTime - firstLoadTime;
        }
        
        public long getLoadCount() {
            return loadCount;
        }
    }
}