package com.waqiti.common.correlation;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for managing correlation IDs across distributed transactions
 * Ensures traceability of requests across multiple microservices
 */
@Slf4j
@Service
public class CorrelationIdService {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String PARENT_ID_KEY = "parentId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String TRACE_ID_KEY = "traceId";
    public static final String REQUEST_ID_KEY = "requestId";
    
    private static final String CORRELATION_ID_PREFIX = "COR-";
    private static final String TRACE_ID_PREFIX = "TRC-";
    private static final String SPAN_ID_PREFIX = "SPN-";
    
    private final ThreadLocal<CorrelationContext> contextHolder = new ThreadLocal<>();

    /**
     * Generate a new correlation ID
     */
    public String generateCorrelationId() {
        String correlationId = CORRELATION_ID_PREFIX + generateUniqueId();
        setCorrelationId(correlationId);
        return correlationId;
    }

    /**
     * Generate a new trace ID for distributed tracing
     */
    public String generateTraceId() {
        return TRACE_ID_PREFIX + generateUniqueId();
    }

    /**
     * Generate a new span ID
     */
    public String generateSpanId() {
        return SPAN_ID_PREFIX + generateShortId();
    }

    /**
     * Set correlation ID in MDC and thread local
     */
    public void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
            getOrCreateContext().setCorrelationId(correlationId);
            log.debug("Set correlation ID: {}", correlationId);
        }
    }

    /**
     * Get current correlation ID
     */
    public Optional<String> getCorrelationId() {
        String mdcValue = MDC.get(CORRELATION_ID_KEY);
        if (mdcValue != null) {
            return Optional.of(mdcValue);
        }
        
        CorrelationContext context = contextHolder.get();
        if (context != null && context.getCorrelationId() != null) {
            return Optional.of(context.getCorrelationId());
        }
        
        return Optional.empty();
    }

    /**
     * Get or create correlation ID
     */
    public String getOrCreateCorrelationId() {
        return getCorrelationId().orElseGet(this::generateCorrelationId);
    }

    /**
     * Set trace context for distributed tracing
     */
    public void setTraceContext(String traceId, String parentSpanId) {
        CorrelationContext context = getOrCreateContext();
        context.setTraceId(traceId);
        context.setParentSpanId(parentSpanId);
        context.setSpanId(generateSpanId());
        
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(PARENT_ID_KEY, parentSpanId);
        MDC.put(SPAN_ID_KEY, context.getSpanId());
        
        log.debug("Set trace context - TraceId: {}, ParentSpanId: {}, SpanId: {}",
                traceId, parentSpanId, context.getSpanId());
    }

    /**
     * Create child span for nested operations
     */
    public String createChildSpan() {
        CorrelationContext context = getOrCreateContext();
        String currentSpanId = context.getSpanId();
        String childSpanId = generateSpanId();
        
        context.setParentSpanId(currentSpanId);
        context.setSpanId(childSpanId);
        
        MDC.put(PARENT_ID_KEY, currentSpanId);
        MDC.put(SPAN_ID_KEY, childSpanId);
        
        log.debug("Created child span: {} (parent: {})", childSpanId, currentSpanId);
        return childSpanId;
    }

    /**
     * Get current correlation context
     */
    public CorrelationContext getCurrentContext() {
        CorrelationContext context = contextHolder.get();
        if (context == null) {
            context = new CorrelationContext();
            context.setCorrelationId(getCorrelationId().orElse(null));
            context.setTraceId(MDC.get(TRACE_ID_KEY));
            context.setSpanId(MDC.get(SPAN_ID_KEY));
            context.setParentSpanId(MDC.get(PARENT_ID_KEY));
        }
        return context;
    }

    /**
     * Clear correlation context
     */
    public void clearContext() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(PARENT_ID_KEY);
        MDC.remove(REQUEST_ID_KEY);
        contextHolder.remove();
        log.debug("Cleared correlation context");
    }

    /**
     * Copy context for async operations
     */
    public CorrelationContext copyContext() {
        CorrelationContext current = getCurrentContext();
        return current.copy();
    }

    /**
     * Restore context in async thread
     */
    public void restoreContext(CorrelationContext context) {
        if (context != null) {
            contextHolder.set(context);
            
            if (context.getCorrelationId() != null) {
                MDC.put(CORRELATION_ID_KEY, context.getCorrelationId());
            }
            if (context.getTraceId() != null) {
                MDC.put(TRACE_ID_KEY, context.getTraceId());
            }
            if (context.getSpanId() != null) {
                MDC.put(SPAN_ID_KEY, context.getSpanId());
            }
            if (context.getParentSpanId() != null) {
                MDC.put(PARENT_ID_KEY, context.getParentSpanId());
            }
            
            log.debug("Restored correlation context: {}", context.getCorrelationId());
        }
    }

    /**
     * Execute with correlation context
     */
    public <T> T executeWithContext(CorrelationContext context, java.util.function.Supplier<T> supplier) {
        CorrelationContext previousContext = copyContext();
        try {
            restoreContext(context);
            return supplier.get();
        } finally {
            restoreContext(previousContext);
        }
    }

    /**
     * Execute async with correlation context
     */
    public void executeAsyncWithContext(CorrelationContext context, Runnable runnable) {
        restoreContext(context);
        try {
            runnable.run();
        } finally {
            clearContext();
        }
    }

    // Private helper methods

    private CorrelationContext getOrCreateContext() {
        CorrelationContext context = contextHolder.get();
        if (context == null) {
            context = new CorrelationContext();
            contextHolder.set(context);
        }
        return context;
    }

    private String generateUniqueId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String generateShortId() {
        return String.format("%016X", ThreadLocalRandom.current().nextLong());
    }

    /**
     * Correlation context holder
     */
    public static class CorrelationContext {
        private String correlationId;
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String requestId;
        private Long timestamp;
        private String userId;
        private String sessionId;
        private java.util.Map<String, String> additionalContext;

        public CorrelationContext() {
            this.timestamp = System.currentTimeMillis();
            this.additionalContext = new java.util.HashMap<>();
        }

        public CorrelationContext copy() {
            CorrelationContext copy = new CorrelationContext();
            copy.correlationId = this.correlationId;
            copy.traceId = this.traceId;
            copy.spanId = this.spanId;
            copy.parentSpanId = this.parentSpanId;
            copy.requestId = this.requestId;
            copy.timestamp = this.timestamp;
            copy.userId = this.userId;
            copy.sessionId = this.sessionId;
            copy.additionalContext = new java.util.HashMap<>(this.additionalContext);
            return copy;
        }

        // Getters and setters
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        
        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }
        
        public String getParentSpanId() { return parentSpanId; }
        public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
        
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public java.util.Map<String, String> getAdditionalContext() { return additionalContext; }
        public void setAdditionalContext(java.util.Map<String, String> additionalContext) { 
            this.additionalContext = additionalContext; 
        }
        
        public void addContext(String key, String value) {
            this.additionalContext.put(key, value);
        }
    }
}