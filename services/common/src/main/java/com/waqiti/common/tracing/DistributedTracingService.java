package com.waqiti.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive distributed tracing service that integrates with Spring Cloud Sleuth,
 * manages correlation IDs, and provides observability across microservices.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
public class DistributedTracingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedTracingService.class);
    
    @Value("${spring.application.name:unknown-service}")
    private String serviceName;
    
    @Value("${waqiti.tracing.enabled:true}")
    private boolean tracingEnabled;
    
    @Value("${waqiti.tracing.sample-rate:0.1}")
    private double sampleRate;
    
    private final Tracer tracer;
    
    private final Map<String, TraceMetrics> traceMetrics = new ConcurrentHashMap<>();
    
    public DistributedTracingService(Tracer tracer) {
        this.tracer = tracer;
    }
    
    @PostConstruct
    public void initialize() {
        CorrelationContext.setServiceName(serviceName);
        logger.info("Distributed tracing service initialized for service: {} (enabled: {}, sample-rate: {})",
                serviceName, tracingEnabled, sampleRate);
    }
    
    /**
     * Starts a new trace for an incoming request.
     *
     * @param operationName the name of the operation
     * @param correlationId existing correlation ID or null to generate new
     * @return the trace context
     */
    @NonNull
    public TraceContext startTrace(@NonNull String operationName, @Nullable String correlationId) {
        String finalCorrelationId = correlationId != null && CorrelationId.isValid(correlationId) 
            ? correlationId 
            : CorrelationId.generate();
        
        CorrelationContext.setCorrelationId(finalCorrelationId);
        
        Span span = null;
        if (tracingEnabled && tracer != null) {
            span = tracer.nextSpan()
                    .name(operationName)
                    .tag("service.name", serviceName)
                    .tag("correlation.id", finalCorrelationId)
                    .start();
            
            CorrelationContext.setTraceId(span.context().traceId());
            CorrelationContext.setSpanId(span.context().spanId());
        }
        
        TraceContext context = new TraceContext(finalCorrelationId, operationName, span, Instant.now());
        updateMetrics(operationName, "started");
        
        logger.debug("Started trace for operation: {} with correlation ID: {}", 
                operationName, CorrelationId.toShortForm(finalCorrelationId));
        
        return context;
    }
    
    /**
     * Creates a child span for the current trace.
     *
     * @param operationName the name of the child operation
     * @return the child trace context
     */
    @NonNull
    public TraceContext startChildTrace(@NonNull String operationName) {
        String correlationId = CorrelationContext.getCorrelationId();
        if (correlationId == null) {
            logger.warn("No correlation ID found in context, starting new trace for operation: {}", operationName);
            return startTrace(operationName, null);
        }
        
        Span childSpan = null;
        if (tracingEnabled && tracer != null) {
            childSpan = tracer.nextSpan()
                    .name(operationName)
                    .tag("service.name", serviceName)
                    .tag("correlation.id", correlationId)
                    .tag("span.kind", "child")
                    .start();
            
            CorrelationContext.setSpanId(childSpan.context().spanId());
        }
        
        TraceContext context = new TraceContext(correlationId, operationName, childSpan, Instant.now());
        updateMetrics(operationName, "child_started");
        
        logger.debug("Started child trace for operation: {} with correlation ID: {}", 
                operationName, CorrelationId.toShortForm(correlationId));
        
        return context;
    }
    
    /**
     * Adds custom tags to the current span.
     *
     * @param tags map of tag keys and values
     */
    public void addTags(@NonNull Map<String, String> tags) {
        if (!tracingEnabled || tracer == null) {
            return;
        }
        
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            tags.forEach((key, value) -> {
                if (value != null) {
                    currentSpan.tag(key, value);
                }
            });
            
            logger.trace("Added tags to current span: {}", tags);
        }
    }
    
    /**
     * Adds a single tag to the current span.
     *
     * @param key the tag key
     * @param value the tag value
     */
    public void addTag(@NonNull String key, @Nullable String value) {
        if (!tracingEnabled || tracer == null || value == null) {
            return;
        }
        
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
            logger.trace("Added tag to current span: {}={}", key, value);
        }
    }
    
    /**
     * Records an event in the current span.
     *
     * @param eventName the event name
     * @param attributes additional event attributes
     */
    public void recordEvent(@NonNull String eventName, @Nullable Map<String, String> attributes) {
        if (!tracingEnabled || tracer == null) {
            return;
        }
        
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.event(eventName);
            if (attributes != null) {
                attributes.forEach(currentSpan::tag);
            }
            
            logger.debug("Recorded event in span: {} with attributes: {}", eventName, attributes);
        }
    }
    
    /**
     * Records an error in the current span.
     *
     * @param throwable the error that occurred
     */
    public void recordError(@NonNull Throwable throwable) {
        if (!tracingEnabled || tracer == null) {
            return;
        }
        
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("error", "true")
                    .tag("error.class", throwable.getClass().getSimpleName())
                    .tag("error.message", throwable.getMessage() != null ? throwable.getMessage() : "Unknown error");
            
            String correlationId = CorrelationContext.getCorrelationId();
            updateMetrics(currentSpan.context().toString(), "error");
            
            logger.error("Recorded error in span with correlation ID: {} - {}", 
                    CorrelationId.toShortForm(correlationId), throwable.getMessage(), throwable);
        }
    }
    
    /**
     * Finishes a trace context.
     *
     * @param context the trace context to finish
     */
    public void finishTrace(@NonNull TraceContext context) {
        if (context.getSpan() != null) {
            context.getSpan().end();
        }
        
        long durationMs = Instant.now().toEpochMilli() - context.getStartTime().toEpochMilli();
        updateMetrics(context.getOperationName(), "completed", durationMs);
        
        logger.debug("Finished trace for operation: {} with correlation ID: {} (duration: {}ms)", 
                context.getOperationName(), 
                CorrelationId.toShortForm(context.getCorrelationId()),
                durationMs);
    }
    
    /**
     * Gets the current trace ID.
     *
     * @return the current trace ID or null if no active trace
     */
    @Nullable
    public String getCurrentTraceId() {
        if (!tracingEnabled || tracer == null) {
            return null;
        }
        
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }
    
    /**
     * Gets the current span ID.
     *
     * @return the current span ID or null if no active span
     */
    @Nullable
    public String getCurrentSpanId() {
        if (!tracingEnabled || tracer == null) {
            return null;
        }
        
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().spanId() : null;
    }
    
    /**
     * Checks if tracing is currently active.
     *
     * @return true if there's an active trace
     */
    public boolean hasActiveTrace() {
        return CorrelationContext.getCorrelationId() != null;
    }
    
    /**
     * Gets tracing metrics for monitoring and observability.
     *
     * @return map of trace metrics
     */
    @NonNull
    public Map<String, TraceMetrics> getTraceMetrics() {
        return new ConcurrentHashMap<>(traceMetrics);
    }
    
    /**
     * Resets trace metrics.
     */
    public void resetMetrics() {
        traceMetrics.clear();
        logger.info("Trace metrics reset for service: {}", serviceName);
    }
    
    private void updateMetrics(String operationName, String eventType) {
        updateMetrics(operationName, eventType, 0L);
    }
    
    private void updateMetrics(String operationName, String eventType, long durationMs) {
        TraceMetrics metrics = traceMetrics.computeIfAbsent(operationName, k -> new TraceMetrics());
        
        switch (eventType) {
            case "started" -> metrics.incrementStarted();
            case "child_started" -> metrics.incrementChildStarted();
            case "completed" -> {
                metrics.incrementCompleted();
                if (durationMs > 0) {
                    metrics.updateDuration(durationMs);
                }
            }
            case "error" -> metrics.incrementErrors();
        }
    }
    
    /**
     * Represents a trace context with metadata.
     */
    public static class TraceContext {
        private final String correlationId;
        private final String operationName;
        private final Span span;
        private final Instant startTime;
        
        public TraceContext(@NonNull String correlationId, @NonNull String operationName, 
                           @Nullable Span span, @NonNull Instant startTime) {
            this.correlationId = correlationId;
            this.operationName = operationName;
            this.span = span;
            this.startTime = startTime;
        }
        
        public String getCorrelationId() { return correlationId; }
        public String getOperationName() { return operationName; }
        public Span getSpan() { return span; }
        public Instant getStartTime() { return startTime; }
    }
    
    /**
     * Metrics for trace operations.
     */
    public static class TraceMetrics {
        private long started = 0;
        private long childStarted = 0;
        private long completed = 0;
        private long errors = 0;
        private long totalDurationMs = 0;
        private long minDurationMs = Long.MAX_VALUE;
        private long maxDurationMs = 0;
        
        public synchronized void incrementStarted() { started++; }
        public synchronized void incrementChildStarted() { childStarted++; }
        public synchronized void incrementCompleted() { completed++; }
        public synchronized void incrementErrors() { errors++; }
        
        public synchronized void updateDuration(long durationMs) {
            totalDurationMs += durationMs;
            minDurationMs = Math.min(minDurationMs, durationMs);
            maxDurationMs = Math.max(maxDurationMs, durationMs);
        }
        
        public synchronized double getAverageDurationMs() {
            return completed > 0 ? (double) totalDurationMs / completed : 0.0;
        }
        
        // Getters
        public long getStarted() { return started; }
        public long getChildStarted() { return childStarted; }
        public long getCompleted() { return completed; }
        public long getErrors() { return errors; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public long getMinDurationMs() { return minDurationMs == Long.MAX_VALUE ? 0 : minDurationMs; }
        public long getMaxDurationMs() { return maxDurationMs; }
    }
}