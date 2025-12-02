package com.waqiti.common.tracing;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe context holder for correlation IDs and tracing information.
 * Provides convenient access to correlation data within the current thread context.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
public final class CorrelationContext {
    
    // MDC keys for structured logging
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";
    public static final String MDC_TRACE_ID_KEY = "traceId";
    public static final String MDC_SPAN_ID_KEY = "spanId";
    public static final String MDC_USER_ID_KEY = "userId";
    public static final String MDC_SERVICE_NAME_KEY = "serviceName";
    public static final String MDC_REQUEST_PATH_KEY = "requestPath";
    public static final String MDC_REQUEST_METHOD_KEY = "requestMethod";
    
    private static final ThreadLocal<CorrelationContextData> CONTEXT = new ThreadLocal<>();
    
    private CorrelationContext() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Sets the correlation ID in the current thread context and MDC.
     *
     * @param correlationId the correlation ID to set
     */
    public static void setCorrelationId(@Nullable String correlationId) {
        if (correlationId != null) {
            getOrCreateContext().correlationId = correlationId;
            MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
        }
    }
    
    /**
     * Gets the correlation ID from the current thread context.
     *
     * @return the correlation ID or null if not set
     */
    @Nullable
    public static String getCorrelationId() {
        CorrelationContextData context = CONTEXT.get();
        return context != null ? context.correlationId : null;
    }
    
    /**
     * Sets the trace ID in the current thread context and MDC.
     *
     * @param traceId the trace ID to set
     */
    public static void setTraceId(@Nullable String traceId) {
        if (traceId != null) {
            getOrCreateContext().traceId = traceId;
            MDC.put(MDC_TRACE_ID_KEY, traceId);
        }
    }
    
    /**
     * Gets the trace ID from the current thread context.
     *
     * @return the trace ID or null if not set
     */
    @Nullable
    public static String getTraceId() {
        CorrelationContextData context = CONTEXT.get();
        return context != null ? context.traceId : null;
    }
    
    /**
     * Sets the span ID in the current thread context and MDC.
     *
     * @param spanId the span ID to set
     */
    public static void setSpanId(@Nullable String spanId) {
        if (spanId != null) {
            getOrCreateContext().spanId = spanId;
            MDC.put(MDC_SPAN_ID_KEY, spanId);
        }
    }
    
    /**
     * Gets the span ID from the current thread context.
     *
     * @return the span ID or null if not set
     */
    @Nullable
    public static String getSpanId() {
        CorrelationContextData context = CONTEXT.get();
        return context != null ? context.spanId : null;
    }
    
    /**
     * Sets the user ID in the current thread context and MDC.
     *
     * @param userId the user ID to set
     */
    public static void setUserId(@Nullable String userId) {
        if (userId != null) {
            getOrCreateContext().userId = userId;
            MDC.put(MDC_USER_ID_KEY, userId);
        }
    }
    
    /**
     * Gets the user ID from the current thread context.
     *
     * @return the user ID or null if not set
     */
    @Nullable
    public static String getUserId() {
        CorrelationContextData context = CONTEXT.get();
        return context != null ? context.userId : null;
    }
    
    /**
     * Sets the service name in the current thread context and MDC.
     *
     * @param serviceName the service name to set
     */
    public static void setServiceName(@Nullable String serviceName) {
        if (serviceName != null) {
            getOrCreateContext().serviceName = serviceName;
            MDC.put(MDC_SERVICE_NAME_KEY, serviceName);
        }
    }
    
    /**
     * Sets request information in the context.
     *
     * @param method the HTTP method
     * @param path the request path
     */
    public static void setRequestInfo(@Nullable String method, @Nullable String path) {
        if (method != null) {
            getOrCreateContext().requestMethod = method;
            MDC.put(MDC_REQUEST_METHOD_KEY, method);
        }
        if (path != null) {
            getOrCreateContext().requestPath = path;
            MDC.put(MDC_REQUEST_PATH_KEY, path);
        }
    }
    
    /**
     * Sets all tracing headers at once for convenience.
     *
     * @param correlationId the correlation ID
     * @param traceId the trace ID
     * @param spanId the span ID
     */
    public static void setTracingInfo(@Nullable String correlationId, 
                                    @Nullable String traceId, 
                                    @Nullable String spanId) {
        setCorrelationId(correlationId);
        setTraceId(traceId);
        setSpanId(spanId);
    }
    
    /**
     * Gets all context data as a map for propagation.
     *
     * @return map containing all context data
     */
    @NonNull
    public static Map<String, String> getContextMap() {
        Map<String, String> contextMap = new ConcurrentHashMap<>();
        CorrelationContextData context = CONTEXT.get();
        
        if (context != null) {
            if (context.correlationId != null) {
                contextMap.put(CorrelationId.CORRELATION_ID_HEADER, context.correlationId);
            }
            if (context.traceId != null) {
                contextMap.put(CorrelationId.TRACE_ID_HEADER, context.traceId);
            }
            if (context.spanId != null) {
                contextMap.put(CorrelationId.SPAN_ID_HEADER, context.spanId);
            }
            if (context.userId != null) {
                contextMap.put("X-User-ID", context.userId);
            }
        }
        
        return contextMap;
    }
    
    /**
     * Clears all context data from the current thread and MDC.
     */
    public static void clear() {
        CONTEXT.remove();
        MDC.remove(MDC_CORRELATION_ID_KEY);
        MDC.remove(MDC_TRACE_ID_KEY);
        MDC.remove(MDC_SPAN_ID_KEY);
        MDC.remove(MDC_USER_ID_KEY);
        MDC.remove(MDC_SERVICE_NAME_KEY);
        MDC.remove(MDC_REQUEST_PATH_KEY);
        MDC.remove(MDC_REQUEST_METHOD_KEY);
    }
    
    /**
     * Copies the current context to be used in async operations.
     *
     * @return a copy of the current context data
     */
    @Nullable
    public static CorrelationContextData copyContext() {
        CorrelationContextData current = CONTEXT.get();
        if (current == null) {
            return null;
        }
        
        return new CorrelationContextData(
            current.correlationId,
            current.traceId,
            current.spanId,
            current.userId,
            current.serviceName,
            current.requestMethod,
            current.requestPath
        );
    }
    
    /**
     * Restores context from a copied context data.
     *
     * @param contextData the context data to restore
     */
    public static void restoreContext(@Nullable CorrelationContextData contextData) {
        if (contextData == null) {
            clear();
            return;
        }
        
        setTracingInfo(contextData.correlationId, contextData.traceId, contextData.spanId);
        setUserId(contextData.userId);
        setServiceName(contextData.serviceName);
        setRequestInfo(contextData.requestMethod, contextData.requestPath);
    }
    
    private static CorrelationContextData getOrCreateContext() {
        CorrelationContextData context = CONTEXT.get();
        if (context == null) {
            context = new CorrelationContextData();
            CONTEXT.set(context);
        }
        return context;
    }
    
    /**
     * Data class to hold correlation context information.
     */
    public static class CorrelationContextData {
        String correlationId;
        String traceId;
        String spanId;
        String userId;
        String serviceName;
        String requestMethod;
        String requestPath;
        
        public CorrelationContextData() {
        }
        
        public CorrelationContextData(String correlationId, String traceId, String spanId, 
                                    String userId, String serviceName, String requestMethod, String requestPath) {
            this.correlationId = correlationId;
            this.traceId = traceId;
            this.spanId = spanId;
            this.userId = userId;
            this.serviceName = serviceName;
            this.requestMethod = requestMethod;
            this.requestPath = requestPath;
        }
        
        // Getters
        public String getCorrelationId() { return correlationId; }
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getUserId() { return userId; }
        public String getServiceName() { return serviceName; }
        public String getRequestMethod() { return requestMethod; }
        public String getRequestPath() { return requestPath; }
    }
}