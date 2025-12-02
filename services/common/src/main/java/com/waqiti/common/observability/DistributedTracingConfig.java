package com.waqiti.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed tracing configuration for microservices correlation
 * Provides automatic trace propagation and correlation across service boundaries
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "waqiti.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class DistributedTracingConfig {
    
    private final Tracer tracer;
    
    @Value("${waqiti.observability.tracing.sample-rate:0.1}")
    private double sampleRate;
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    // Common trace headers
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    
    // Active spans tracking
    private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();
    
    /**
     * HTTP interceptor for automatic trace correlation
     */
    @Bean
    public HandlerInterceptor tracingInterceptor() {
        return new TracingInterceptor();
    }
    
    /**
     * Create a new span with correlation context
     */
    public Span createSpan(String operationName) {
        return createSpan(operationName, null);
    }
    
    /**
     * Create a new span with parent context
     */
    public Span createSpan(String operationName, Span parent) {
        Span span = tracer.nextSpan()
            .name(operationName)
            .tag("service.name", serviceName)
            .tag("operation.name", operationName);
        
        if (parent != null) {
            // Note: setParent is not available in Micrometer Tracing API
            // Parent context is handled automatically by the tracer
        }
        
        span.start();
        
        // Store span for potential child operations
        String spanId = span.context().spanId();
        activeSpans.put(spanId, span);
        
        return span;
    }
    
    /**
     * Create span for database operations
     */
    public Span createDatabaseSpan(String operation, String table) {
        return createSpan("db." + operation)
            .tag("db.operation", operation)
            .tag("db.table", table)
            .tag("component", "database");
    }
    
    /**
     * Create span for HTTP client operations
     */
    public Span createHttpClientSpan(String method, String url) {
        return createSpan("http.client." + method.toLowerCase())
            .tag("http.method", method)
            .tag("http.url", url)
            .tag("component", "http-client");
    }
    
    /**
     * Create span for business operations
     */
    public Span createBusinessSpan(String operation, String component) {
        return createSpan("business." + operation)
            .tag("business.operation", operation)
            .tag("business.component", component)
            .tag("component", "business-logic");
    }
    
    /**
     * Create span for cache operations
     */
    public Span createCacheSpan(String operation, String cacheName) {
        return createSpan("cache." + operation)
            .tag("cache.operation", operation)
            .tag("cache.name", cacheName)
            .tag("component", "cache");
    }
    
    /**
     * Create span for message queue operations
     */
    public Span createMessageSpan(String operation, String queue) {
        return createSpan("message." + operation)
            .tag("message.operation", operation)
            .tag("message.queue", queue)
            .tag("component", "messaging");
    }
    
    /**
     * Add user context to current span
     */
    public void addUserContext(String userId, String tenantId) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            if (userId != null) {
                currentSpan.tag("user.id", userId);
            }
            if (tenantId != null) {
                currentSpan.tag("tenant.id", tenantId);
            }
        }
    }
    
    /**
     * Add business context to current span
     */
    public void addBusinessContext(String entity, String entityId, String operation) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("business.entity", entity);
            if (entityId != null) {
                currentSpan.tag("business.entity.id", entityId);
            }
            currentSpan.tag("business.operation", operation);
        }
    }
    
    /**
     * Add error context to current span
     */
    public void addErrorContext(Exception exception) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("error", "true")
                .tag("error.type", exception.getClass().getSimpleName())
                .tag("error.message", exception.getMessage() != null ? 
                    exception.getMessage() : "Unknown error");
        }
    }
    
    /**
     * Add custom tags to current span
     */
    public void addTags(Map<String, String> tags) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null && tags != null) {
            tags.forEach(currentSpan::tag);
        }
    }
    
    /**
     * Execute operation within a span
     */
    public <T> T executeWithSpan(String operationName, java.util.function.Supplier<T> operation) {
        Span span = createSpan(operationName);
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            return operation.get();
        } catch (Exception e) {
            addErrorContext(e);
            throw e;
        } finally {
            span.end();
            activeSpans.remove(span.context().spanId());
        }
    }
    
    /**
     * Execute void operation within a span
     */
    public void executeWithSpan(String operationName, Runnable operation) {
        executeWithSpan(operationName, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Execute database operation within a span
     */
    public <T> T executeWithDatabaseSpan(String operation, String table, 
                                        java.util.function.Supplier<T> dbOperation) {
        Span span = createDatabaseSpan(operation, table);
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            T result = dbOperation.get();
            span.tag("db.success", "true");
            return result;
        } catch (Exception e) {
            span.tag("db.success", "false");
            addErrorContext(e);
            throw e;
        } finally {
            span.end();
            activeSpans.remove(span.context().spanId());
        }
    }
    
    /**
     * Execute HTTP client operation within a span
     */
    public <T> T executeWithHttpClientSpan(String method, String url, 
                                          java.util.function.Supplier<T> httpOperation) {
        Span span = createHttpClientSpan(method, url);
        try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
            T result = httpOperation.get();
            span.tag("http.success", "true");
            return result;
        } catch (Exception e) {
            span.tag("http.success", "false");
            addErrorContext(e);
            throw e;
        } finally {
            span.end();
            activeSpans.remove(span.context().spanId());
        }
    }
    
    /**
     * Get correlation headers for outbound requests
     */
    public Map<String, String> getCorrelationHeaders() {
        Map<String, String> headers = new ConcurrentHashMap<>();
        
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            headers.put(TRACE_ID_HEADER, currentSpan.context().traceId());
            headers.put(SPAN_ID_HEADER, currentSpan.context().spanId());
        }
        
        return headers;
    }
    
    /**
     * Create correlation ID for request tracking
     */
    public String createCorrelationId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceId();
        }
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Get current trace ID
     */
    public String getCurrentTraceId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }
    
    /**
     * Get current span ID
     */
    public String getCurrentSpanId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().spanId() : null;
    }
    
    /**
     * HTTP interceptor for automatic request tracing
     */
    private class TracingInterceptor implements HandlerInterceptor {
        
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                               Object handler) throws Exception {
            String traceId = request.getHeader(TRACE_ID_HEADER);
            String spanId = request.getHeader(SPAN_ID_HEADER);
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            String userId = request.getHeader(USER_ID_HEADER);
            String tenantId = request.getHeader(TENANT_ID_HEADER);
            
            Span span = createSpan("http.server." + request.getMethod().toLowerCase())
                .tag("http.method", request.getMethod())
                .tag("http.url", request.getRequestURL().toString())
                .tag("http.path", request.getRequestURI())
                .tag("component", "http-server");
            
            if (traceId != null) {
                span.tag("parent.trace.id", traceId);
            }
            if (correlationId != null) {
                span.tag("correlation.id", correlationId);
            }
            if (userId != null) {
                span.tag("user.id", userId);
            }
            if (tenantId != null) {
                span.tag("tenant.id", tenantId);
            }
            
            // Set response headers for trace correlation
            response.setHeader(TRACE_ID_HEADER, span.context().traceId());
            response.setHeader(SPAN_ID_HEADER, span.context().spanId());
            
            // Start span scope
            tracer.withSpan(span);
            
            return true;
        }
        
        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                  Object handler, Exception ex) throws Exception {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.tag("http.status_code", String.valueOf(response.getStatus()));
                
                if (response.getStatus() >= 400) {
                    currentSpan.tag("error", "true");
                }
                
                if (ex != null) {
                    addErrorContext(ex);
                }
                
                currentSpan.end();
                activeSpans.remove(currentSpan.context().spanId());
            }
        }
    }
    
    /**
     * Get tracing statistics
     */
    public TracingStats getTracingStats() {
        return TracingStats.builder()
            .activeSpansCount(activeSpans.size())
            .sampleRate(sampleRate)
            .serviceName(serviceName)
            .build();
    }
    
    /**
     * Tracing statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class TracingStats {
        private final int activeSpansCount;
        private final double sampleRate;
        private final String serviceName;
    }
}