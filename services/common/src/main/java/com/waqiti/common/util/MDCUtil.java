package com.waqiti.common.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * MDC (Mapped Diagnostic Context) Utility
 * Provides utilities for managing MDC context for distributed logging and tracing
 *
 * Used by Kafka consumers and REST controllers to maintain trace context across threads
 */
public class MDCUtil {

    // Standard MDC keys
    public static final String REQUEST_ID = "requestId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String USER_ID = "userId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String SESSION_ID = "sessionId";
    public static final String SERVICE_NAME = "serviceName";

    private MDCUtil() {
        // Utility class - private constructor
    }

    /**
     * Set request ID in MDC context
     *
     * @param requestId The request ID
     */
    public static void setRequestId(String requestId) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(REQUEST_ID, requestId);
        }
    }

    /**
     * Get request ID from MDC context
     *
     * @return The request ID or null if not set
     */
    public static String getRequestId() {
        return MDC.get(REQUEST_ID);
    }

    /**
     * Generate and set a new request ID
     *
     * @return The generated request ID
     */
    public static String generateRequestId() {
        String requestId = UUID.randomUUID().toString();
        setRequestId(requestId);
        return requestId;
    }

    /**
     * Set trace ID in MDC context
     *
     * @param traceId The trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    /**
     * Get trace ID from MDC context
     *
     * @return The trace ID or null if not set
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * Set span ID in MDC context
     *
     * @param spanId The span ID
     */
    public static void setSpanId(String spanId) {
        if (spanId != null && !spanId.isEmpty()) {
            MDC.put(SPAN_ID, spanId);
        }
    }

    /**
     * Get span ID from MDC context
     *
     * @return The span ID or null if not set
     */
    public static String getSpanId() {
        return MDC.get(SPAN_ID);
    }

    /**
     * Set user ID in MDC context
     *
     * @param userId The user ID
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            MDC.put(USER_ID, userId);
        }
    }

    /**
     * Get user ID from MDC context
     *
     * @return The user ID or null if not set
     */
    public static String getUserId() {
        return MDC.get(USER_ID);
    }

    /**
     * Set correlation ID in MDC context
     *
     * @param correlationId The correlation ID
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }

    /**
     * Get correlation ID from MDC context
     *
     * @return The correlation ID or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID);
    }

    /**
     * Set session ID in MDC context
     *
     * @param sessionId The session ID
     */
    public static void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            MDC.put(SESSION_ID, sessionId);
        }
    }

    /**
     * Get session ID from MDC context
     *
     * @return The session ID or null if not set
     */
    public static String getSessionId() {
        return MDC.get(SESSION_ID);
    }

    /**
     * Set service name in MDC context
     *
     * @param serviceName The service name
     */
    public static void setServiceName(String serviceName) {
        if (serviceName != null && !serviceName.isEmpty()) {
            MDC.put(SERVICE_NAME, serviceName);
        }
    }

    /**
     * Get service name from MDC context
     *
     * @return The service name or null if not set
     */
    public static String getServiceName() {
        return MDC.get(SERVICE_NAME);
    }

    /**
     * Put a custom key-value pair in MDC context
     *
     * @param key The MDC key
     * @param value The MDC value
     */
    public static void put(String key, String value) {
        if (key != null && !key.isEmpty() && value != null) {
            MDC.put(key, value);
        }
    }

    /**
     * Get a value from MDC context
     *
     * @param key The MDC key
     * @return The value or null if not set
     */
    public static String get(String key) {
        return MDC.get(key);
    }

    /**
     * Remove a key from MDC context
     *
     * @param key The MDC key to remove
     */
    public static void remove(String key) {
        MDC.remove(key);
    }

    /**
     * Clear all MDC context
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Get a copy of the current MDC context
     *
     * @return Map containing current MDC context
     */
    public static Map<String, String> getCopyOfContextMap() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * Set the MDC context from a map
     *
     * @param contextMap The context map to set
     */
    public static void setContextMap(Map<String, String> contextMap) {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }

    /**
     * Wrap a Runnable with MDC context propagation
     * Ensures MDC context is preserved when executing in different threads
     *
     * @param runnable The runnable to wrap
     * @return Wrapped runnable with MDC context
     */
    public static Runnable wrap(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Wrap a Callable with MDC context propagation
     * Ensures MDC context is preserved when executing in different threads
     *
     * @param callable The callable to wrap
     * @param <T> The return type
     * @return Wrapped callable with MDC context
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                return callable.call();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Initialize MDC with standard trace IDs
     * Useful for starting a new trace context
     *
     * @return The generated trace ID
     */
    public static String initializeTraceContext() {
        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString();
        setTraceId(traceId);
        setSpanId(spanId);
        return traceId;
    }

    /**
     * Initialize MDC with standard trace IDs and request ID
     *
     * @param requestId The request ID to use
     * @return The generated trace ID
     */
    public static String initializeTraceContext(String requestId) {
        setRequestId(requestId);
        return initializeTraceContext();
    }
}
