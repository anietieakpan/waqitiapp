package com.waqiti.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provides access to the current tracing context.
 * Useful for accessing trace and span IDs for logging and correlation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TracingContextProvider {
    
    private final Tracer tracer;
    
    /**
     * Gets the current trace ID.
     *
     * @return The current trace ID or empty if not in a traced context
     */
    public Optional<String> getCurrentTraceId() {
        try {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                return Optional.of(currentSpan.context().traceId());
            }
        } catch (Exception e) {
            log.debug("Failed to get current trace ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Gets the current span ID.
     *
     * @return The current span ID or empty if not in a traced context
     */
    public Optional<String> getCurrentSpanId() {
        try {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                return Optional.of(currentSpan.context().spanId());
            }
        } catch (Exception e) {
            log.debug("Failed to get current span ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Gets the current parent span ID.
     *
     * @return The parent span ID or empty if not available
     */
    public Optional<String> getParentSpanId() {
        try {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                return Optional.ofNullable(currentSpan.context().parentId());
            }
        } catch (Exception e) {
            log.debug("Failed to get parent span ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Checks if we are currently in a traced context.
     *
     * @return true if in a traced context, false otherwise
     */
    public boolean isTracing() {
        return tracer.currentSpan() != null;
    }
    
    /**
     * Creates a new span as a child of the current span.
     *
     * @param name The name of the new span
     * @return The new span
     */
    public Span createChildSpan(String name) {
        return tracer.nextSpan().name(name);
    }
    
    /**
     * Adds a tag to the current span.
     *
     * @param key The tag key
     * @param value The tag value
     */
    public void addTagToCurrentSpan(String key, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        } else {
            log.debug("No current span to add tag: {}={}", key, value);
        }
    }
    
    /**
     * Adds an event to the current span.
     *
     * @param eventName The name of the event
     */
    public void addEventToCurrentSpan(String eventName) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.event(eventName);
        } else {
            log.debug("No current span to add event: {}", eventName);
        }
    }
    
    /**
     * Gets the correlation ID for the current trace.
     * This can be used for logging and debugging.
     *
     * @return The correlation ID (trace ID) or a default value
     */
    public String getCorrelationId() {
        return getCurrentTraceId().orElse("no-trace-id");
    }
    
    /**
     * Creates a formatted string with trace context for logging.
     *
     * @param message The log message
     * @return The message with trace context
     */
    public String withTraceContext(String message) {
        Optional<String> traceId = getCurrentTraceId();
        Optional<String> spanId = getCurrentSpanId();
        
        if (traceId.isPresent() || spanId.isPresent()) {
            return String.format("[trace:%s span:%s] %s", 
                traceId.orElse("none"), 
                spanId.orElse("none"), 
                message);
        }
        return message;
    }
}