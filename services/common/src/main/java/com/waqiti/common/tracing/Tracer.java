package com.waqiti.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Distributed tracing component for tracking operations across microservices.
 * Provides methods for creating, managing, and annotating trace spans.
 */
@Component
@Slf4j
public class Tracer {

    private final io.micrometer.tracing.Tracer micrometerTracer;
    private final ThreadLocal<Map<String, Span>> activeSpans = ThreadLocal.withInitial(HashMap::new);

    public Tracer(io.micrometer.tracing.Tracer micrometerTracer) {
        this.micrometerTracer = micrometerTracer;
    }

    /**
     * Start a new trace span
     */
    public Span startSpan(String operationName) {
        Span span = micrometerTracer.nextSpan()
            .name(operationName)
            .start();
        
        activeSpans.get().put(operationName, span);
        log.debug("Started trace span: {}", operationName);
        
        return span;
    }

    /**
     * Start a new trace span with parent
     */
    public Span startSpan(String operationName, Span parent) {
        Span span = micrometerTracer.nextSpan(parent)
            .name(operationName)
            .start();
        
        activeSpans.get().put(operationName, span);
        log.debug("Started child trace span: {} with parent: {}", operationName, parent.context().spanId());
        
        return span;
    }

    /**
     * Get the current active span
     */
    public Optional<Span> getCurrentSpan() {
        return Optional.ofNullable(micrometerTracer.currentSpan());
    }

    /**
     * Get a specific active span by name
     */
    public Optional<Span> getSpan(String operationName) {
        return Optional.ofNullable(activeSpans.get().get(operationName));
    }

    /**
     * Add a tag to the current span
     */
    public void tag(String key, String value) {
        getCurrentSpan().ifPresent(span -> {
            span.tag(key, value);
            log.debug("Added tag to span: {}={}", key, value);
        });
    }

    /**
     * Add tags to a specific span
     */
    public void tag(Span span, String key, String value) {
        if (span != null) {
            span.tag(key, value);
            log.debug("Added tag to span {}: {}={}", span.context().spanId(), key, value);
        }
    }

    /**
     * Add multiple tags to the current span
     */
    public void tags(Map<String, String> tags) {
        getCurrentSpan().ifPresent(span -> {
            tags.forEach(span::tag);
            log.debug("Added {} tags to current span", tags.size());
        });
    }

    /**
     * Add an event to the current span
     */
    public void event(String eventName) {
        getCurrentSpan().ifPresent(span -> {
            span.event(eventName);
            log.debug("Added event to span: {}", eventName);
        });
    }

    /**
     * Add an error to the current span
     */
    public void error(Throwable throwable) {
        getCurrentSpan().ifPresent(span -> {
            span.error(throwable);
            span.tag("error", "true");
            span.tag("error.type", throwable.getClass().getSimpleName());
            span.tag("error.message", throwable.getMessage() != null ? throwable.getMessage() : "Unknown error");
            log.debug("Added error to span: {}", throwable.getMessage());
        });
    }

    /**
     * End a span
     */
    public void endSpan(Span span) {
        if (span != null) {
            span.end();
            
            // Remove from active spans
            activeSpans.get().entrySet().removeIf(entry -> entry.getValue().equals(span));
            
            log.debug("Ended trace span: {}", span.context().spanId());
        }
    }

    /**
     * End a span by name
     */
    public void endSpan(String operationName) {
        Span span = activeSpans.get().remove(operationName);
        if (span != null) {
            span.end();
            log.debug("Ended trace span: {}", operationName);
        }
    }

    /**
     * Get the current trace ID
     */
    public String getTraceId() {
        return getCurrentSpan()
            .map(span -> span.context().traceId())
            .orElse("no-trace");
    }

    /**
     * Get the current span ID
     */
    public String getSpanId() {
        return getCurrentSpan()
            .map(span -> span.context().spanId())
            .orElse("no-span");
    }

    /**
     * Get the current trace context
     */
    public Optional<TraceContext> getTraceContext() {
        return getCurrentSpan().map(Span::context);
    }

    /**
     * Create a new scope with the given span
     */
    public AutoCloseable withSpan(Span span) {
        if (span != null) {
            return micrometerTracer.withSpan(span);
        }
        return () -> {}; // No-op closeable
    }

    /**
     * Clear all active spans for the current thread
     */
    public void clearActiveSpans() {
        Map<String, Span> spans = activeSpans.get();
        spans.values().forEach(this::endSpan);
        spans.clear();
        activeSpans.remove();
    }

    /**
     * Create a baggage item that will be propagated to child spans
     */
    public void setBaggage(String key, String value) {
        getCurrentSpan().ifPresent(span -> {
            // Baggage implementation would depend on the tracing system
            span.tag("baggage." + key, value);
            log.debug("Set baggage item: {}={}", key, value);
        });
    }

    /**
     * Extract trace context from headers for cross-service propagation
     */
    public TraceContext extractContext(Map<String, String> headers) {
        // This would need to be implemented based on the specific tracing system
        // For now, return current context
        return getTraceContext().orElse(null);
    }

    /**
     * Inject trace context into headers for cross-service propagation
     */
    public void injectContext(Map<String, String> headers) {
        getTraceContext().ifPresent(context -> {
            headers.put("X-Trace-Id", context.traceId());
            headers.put("X-Span-Id", context.spanId());
            headers.put("X-Parent-Span-Id", context.parentId() != null ? context.parentId() : "");
            log.debug("Injected trace context into headers: traceId={}", context.traceId());
        });
    }
}