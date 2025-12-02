package com.waqiti.monitoring.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed tracing service using OpenTelemetry
 * Provides correlation across microservices
 */
@Service
@Slf4j
public class TracingService {
    
    private Tracer tracer;
    private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        this.tracer = GlobalOpenTelemetry.getTracer("waqiti-monitoring", "1.0.0");
        log.info("Tracing service initialized with OpenTelemetry");
    }
    
    /**
     * Start a new trace span
     */
    public Span startSpan(String name, SpanKind kind) {
        Span span = tracer.spanBuilder(name)
            .setSpanKind(kind)
            .startSpan();
        
        activeSpans.put(span.getSpanContext().getSpanId(), span);
        return span;
    }
    
    /**
     * Start a child span
     */
    public Span startChildSpan(String name, Span parent) {
        Span span = tracer.spanBuilder(name)
            .setParent(Context.current().with(parent))
            .startSpan();
        
        activeSpans.put(span.getSpanContext().getSpanId(), span);
        return span;
    }
    
    /**
     * End a span with success
     */
    public void endSpanSuccess(Span span) {
        span.setStatus(StatusCode.OK);
        span.end();
        activeSpans.remove(span.getSpanContext().getSpanId());
    }
    
    /**
     * End a span with error
     */
    public void endSpanError(Span span, Throwable error) {
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
        span.end();
        activeSpans.remove(span.getSpanContext().getSpanId());
    }
    
    /**
     * Add attributes to a span
     */
    public void addSpanAttribute(Span span, String key, String value) {
        span.setAttribute(key, value);
    }
    
    public void addSpanAttribute(Span span, String key, long value) {
        span.setAttribute(key, value);
    }
    
    public void addSpanAttribute(Span span, String key, double value) {
        span.setAttribute(key, value);
    }
    
    public void addSpanAttribute(Span span, String key, boolean value) {
        span.setAttribute(key, value);
    }
    
    /**
     * Add event to span
     */
    public void addSpanEvent(Span span, String name, Map<String, Object> attributes) {
        span.addEvent(name);
    }
    
    /**
     * Extract trace context from headers
     */
    public Context extractContext(Map<String, String> headers) {
        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), headers, new TextMapGetter<Map<String, String>>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }
                
                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier.get(key);
                }
            });
    }
    
    /**
     * Inject trace context into headers
     */
    public void injectContext(Context context, Map<String, String> headers) {
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .inject(context, headers, new TextMapSetter<Map<String, String>>() {
                @Override
                public void set(Map<String, String> carrier, String key, String value) {
                    carrier.put(key, value);
                }
            });
    }
    
    /**
     * Get current trace ID
     */
    public String getCurrentTraceId() {
        Span currentSpan = Span.current();
        return currentSpan.getSpanContext().getTraceId();
    }
    
    /**
     * Get current span ID
     */
    public String getCurrentSpanId() {
        Span currentSpan = Span.current();
        return currentSpan.getSpanContext().getSpanId();
    }
    
    /**
     * Cleanup inactive spans
     */
    public void cleanupInactiveSpans() {
        // In production, this would have more sophisticated cleanup logic
        log.debug("Active spans: {}", activeSpans.size());
    }
}