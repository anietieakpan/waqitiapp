package com.waqiti.common.servicemesh;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability Manager for Service Mesh
 * Handles metrics collection, distributed tracing, and logging aggregation
 */
@Slf4j
@Data
@Builder
@RequiredArgsConstructor
public class ObservabilityManager {

    private final MeterRegistry meterRegistry;
    @Builder.Default
    private final boolean tracingEnabled = true;
    private final String tracingEndpoint;
    @Builder.Default
    private final boolean metricsEnabled = true;
    @Builder.Default
    private final int metricsPort = 9090;
    @Builder.Default
    private final boolean accessLogEnabled = true;
    private final Tracer tracer;
    @Builder.Default
    private final boolean distributedTracingEnabled = true;

    @Builder.Default
    private final Map<String, ServiceMetrics> serviceMetrics = new ConcurrentHashMap<>();
    @Builder.Default
    private final Map<String, TraceContext> activeTraces = new ConcurrentHashMap<>();
    @Builder.Default
    private final Map<String, AccessLogEntry> accessLogs = new ConcurrentHashMap<>();
    
    @Builder.Default
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize observability manager
     */
    public void initialize(ServiceMeshProperties.ObservabilityConfig config) {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing Observability Manager - Tracing: {}, Metrics: {}, Access Logs: {}", 
                    tracingEnabled, metricsEnabled, accessLogEnabled);
            
            if (tracingEnabled) {
                initializeTracing(config);
            }
            
            if (metricsEnabled) {
                initializeMetrics();
            }
            
            if (accessLogEnabled) {
                initializeAccessLogging();
            }
            
            log.info("Observability Manager initialized successfully");
        }
    }

    /**
     * Configure tracing for a service
     */
    public void configureTracing(String serviceName) {
        if (!tracingEnabled) {
            return;
        }
        
        log.debug("Configuring tracing for service: {}", serviceName);
        
        ServiceMetrics metrics = serviceMetrics.computeIfAbsent(serviceName, 
                k -> new ServiceMetrics(serviceName, meterRegistry));
    }

    /**
     * Start a new trace span
     */
    public TraceSpan startSpan(SpanRequest request) {
        if (!tracingEnabled || tracer == null) {
            return TraceSpan.builder()
                    .spanId(UUID.randomUUID().toString())
                    .traceId(request.getTraceId() != null ? request.getTraceId() : UUID.randomUUID().toString())
                    .build();
        }
        
        Span span = tracer.spanBuilder(request.getOperationName())
                .setSpanKind(SpanKind.valueOf(request.getSpanKind().name()))
                .startSpan();
        
        // Add span attributes
        if (request.getAttributes() != null) {
            request.getAttributes().forEach(span::setAttribute);
        }
        
        TraceContext context = TraceContext.builder()
                .traceId(span.getSpanContext().getTraceId())
                .spanId(span.getSpanContext().getSpanId())
                .span(span)
                .startTime(Instant.now())
                .serviceName(request.getServiceName())
                .operationName(request.getOperationName())
                .build();
        
        activeTraces.put(context.getSpanId(), context);
        
        return TraceSpan.builder()
                .traceId(context.getTraceId())
                .spanId(context.getSpanId())
                .parentSpanId(request.getParentSpanId())
                .operationName(request.getOperationName())
                .startTime(context.getStartTime())
                .build();
    }

    /**
     * End a trace span
     */
    public void endSpan(String spanId, SpanStatus status) {
        TraceContext context = activeTraces.remove(spanId);
        
        if (context != null && context.getSpan() != null) {
            Span span = context.getSpan();
            
            // Set status
            if (status.isError()) {
                span.setAttribute("error", true);
                if (status.getErrorMessage() != null) {
                    span.setAttribute("error.message", status.getErrorMessage());
                }
            }
            
            // Record duration
            Duration duration = Duration.between(context.getStartTime(), Instant.now());
            span.setAttribute("duration.ms", duration.toMillis());
            
            span.end();
            
            // Record metrics
            recordSpanMetrics(context, duration, status);
        }
    }

    /**
     * Record custom metric
     */
    public void recordMetric(MetricRequest request) {
        if (!metricsEnabled) {
            return;
        }
        
        switch (request.getType()) {
            case COUNTER:
                meterRegistry.counter(request.getName(), request.getTags()).increment(request.getValue());
                break;
                
            case GAUGE:
                meterRegistry.gauge(request.getName(), request.getTags(), request.getValue());
                break;
                
            case TIMER:
                io.micrometer.core.instrument.Timer timer = meterRegistry.timer(request.getName(), request.getTags());
                timer.record((long) request.getValue(), TimeUnit.MILLISECONDS);
                break;
                
            case HISTOGRAM:
                meterRegistry.summary(request.getName(), request.getTags()).record(request.getValue());
                break;
        }
    }

    /**
     * Log access entry
     */
    public void logAccess(AccessLogRequest request) {
        if (!accessLogEnabled) {
            return;
        }
        
        AccessLogEntry entry = AccessLogEntry.builder()
                .timestamp(Instant.now())
                .serviceName(request.getServiceName())
                .method(request.getMethod())
                .path(request.getPath())
                .statusCode(request.getStatusCode())
                .responseTime(request.getResponseTime())
                .clientIp(request.getClientIp())
                .userAgent(request.getUserAgent())
                .traceId(request.getTraceId())
                .build();
        
        // In production, this would write to a centralized logging system
        log.info("Access: {} {} {} {} {}ms", 
                entry.getMethod(), 
                entry.getPath(), 
                entry.getStatusCode(),
                entry.getClientIp(),
                entry.getResponseTime());
        
        // Store for analysis
        accessLogs.put(UUID.randomUUID().toString(), entry);
        
        // Cleanup old entries (keep last 1000)
        if (accessLogs.size() > 1000) {
            accessLogs.keySet().stream()
                    .limit(100)
                    .forEach(accessLogs::remove);
        }
    }

    /**
     * Get service metrics
     */
    public ServiceMetricsSnapshot getServiceMetrics(String serviceName) {
        ServiceMetrics metrics = serviceMetrics.get(serviceName);
        
        if (metrics == null) {
            return ServiceMetricsSnapshot.builder()
                    .serviceName(serviceName)
                    .timestamp(Instant.now())
                    .build();
        }
        
        return ServiceMetricsSnapshot.builder()
                .serviceName(serviceName)
                .requestCount(metrics.getRequestCount().get())
                .errorCount(metrics.getErrorCount().get())
                .averageResponseTime(metrics.getAverageResponseTime())
                .p95ResponseTime(metrics.getP95ResponseTime())
                .p99ResponseTime(metrics.getP99ResponseTime())
                .activeRequests(metrics.getActiveRequests().get())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Get trace information
     */
    public TraceInfo getTraceInfo(String traceId) {
        List<TraceContext> traceSpans = activeTraces.values().stream()
                .filter(ctx -> ctx.getTraceId().equals(traceId))
                .toList();
        
        return TraceInfo.builder()
                .traceId(traceId)
                .spanCount(traceSpans.size())
                .services(traceSpans.stream()
                        .map(TraceContext::getServiceName)
                        .distinct()
                        .toList())
                .startTime(traceSpans.stream()
                        .map(TraceContext::getStartTime)
                        .min(Instant::compareTo)
                        .orElse(null))
                .build();
    }

    /**
     * Export metrics
     */
    public MetricsExport exportMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        
        // Collect all metrics
        meterRegistry.getMeters().forEach(meter -> {
            String name = meter.getId().getName();
            
            if (meter instanceof Counter) {
                metrics.put(name, ((Counter) meter).count());
            } else if (meter instanceof Gauge) {
                metrics.put(name, ((Gauge) meter).value());
            } else if (meter instanceof io.micrometer.core.instrument.Timer) {
                metrics.put(name + ".count", (double) ((io.micrometer.core.instrument.Timer) meter).count());
                metrics.put(name + ".mean", ((io.micrometer.core.instrument.Timer) meter).mean(TimeUnit.MILLISECONDS));
            }
        });
        
        return MetricsExport.builder()
                .timestamp(Instant.now())
                .metrics(metrics)
                .build();
    }

    /**
     * Remove tracing for a service
     */
    public void removeTracing(String serviceName) {
        log.debug("Removing tracing for service: {}", serviceName);
        serviceMetrics.remove(serviceName);
    }

    /**
     * Apply observability configuration
     */
    public void applyConfiguration(ServiceMeshManager.ObservabilityConfiguration config) {
        log.info("Applying observability configuration");
        // Implementation would update observability settings based on configuration
    }

    /**
     * Flush pending data
     */
    public void flush() {
        log.info("Flushing observability data");
        
        // Force export of metrics
        if (metricsEnabled) {
            meterRegistry.close();
        }
        
        // End all active spans
        activeTraces.values().forEach(context -> {
            if (context.getSpan() != null) {
                context.getSpan().end();
            }
        });
        activeTraces.clear();
    }

    // Private helper methods

    private void initializeTracing(ServiceMeshProperties.ObservabilityConfig config) {
        try {
            log.info("Initializing distributed tracing with endpoint: {}", tracingEndpoint);
            
            // Tracer should be passed via constructor/builder
            // Additional tracing configuration can be done here
            
            log.info("Distributed tracing initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize tracing", e);
        }
    }

    private void initializeMetrics() {
        log.info("Initializing metrics collection on port: {}", metricsPort);
        
        // Register standard JVM metrics
        new io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics().bindTo(meterRegistry);
        new io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics().bindTo(meterRegistry);
        new io.micrometer.core.instrument.binder.jvm.JvmGcMetrics().bindTo(meterRegistry);
        new io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics().bindTo(meterRegistry);
        
        log.info("Metrics collection initialized successfully");
    }

    private void initializeAccessLogging() {
        log.info("Initializing access logging");
        // In production, this would configure centralized logging
    }

    private void recordSpanMetrics(TraceContext context, Duration duration, SpanStatus status) {
        ServiceMetrics metrics = serviceMetrics.get(context.getServiceName());
        
        if (metrics != null) {
            metrics.recordRequest(duration.toMillis(), status.isError());
        }
        
        // Record global metrics
        meterRegistry.counter("service.mesh.spans.total",
                Tags.of("service", context.getServiceName(),
                        "operation", context.getOperationName(),
                        "status", status.isError() ? "error" : "success"))
                .increment();
        
        meterRegistry.timer("service.mesh.span.duration",
                Tags.of("service", context.getServiceName(),
                        "operation", context.getOperationName()))
                .record(duration);
    }

    // Inner classes

    @Data
    private static class ServiceMetrics {
        private final String serviceName;
        private final AtomicLong requestCount = new AtomicLong();
        private final AtomicLong errorCount = new AtomicLong();
        private final io.micrometer.core.instrument.Timer responseTimer;
        private final AtomicLong activeRequests = new AtomicLong();
        
        public ServiceMetrics(String serviceName, MeterRegistry registry) {
            this.serviceName = serviceName;
            this.responseTimer = registry.timer("service.response.time", "service", serviceName);
        }
        
        public void recordRequest(long duration, boolean error) {
            requestCount.incrementAndGet();
            if (error) {
                errorCount.incrementAndGet();
            }
            responseTimer.record(duration, TimeUnit.MILLISECONDS);
        }
        
        public AtomicLong getRequestCount() {
            return requestCount;
        }
        
        public AtomicLong getErrorCount() {
            return errorCount;
        }
        
        public AtomicLong getActiveRequests() {
            return activeRequests;
        }
        
        public double getAverageResponseTime() {
            return responseTimer.mean(TimeUnit.MILLISECONDS);
        }
        
        public double getP95ResponseTime() {
            HistogramSnapshot snapshot = responseTimer.takeSnapshot();
            return snapshot.percentileValues()[0].value(TimeUnit.MILLISECONDS);
        }
        
        public double getP99ResponseTime() {
            HistogramSnapshot snapshot = responseTimer.takeSnapshot();
            return snapshot.percentileValues()[1].value(TimeUnit.MILLISECONDS);
        }
    }

    @Data
    @Builder
    public static class TraceContext {
        private String traceId;
        private String spanId;
        private Span span;
        private Instant startTime;
        private String serviceName;
        private String operationName;
    }

    @Data
    @Builder
    public static class SpanRequest {
        private String traceId;
        private String parentSpanId;
        private String serviceName;
        private String operationName;
        private SpanKind spanKind;
        private Map<String, String> attributes;
        
        public enum SpanKind {
            INTERNAL,
            SERVER,
            CLIENT,
            PRODUCER,
            CONSUMER
        }
    }

    @Data
    @Builder
    public static class TraceSpan {
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String operationName;
        private Instant startTime;
    }

    @Data
    @Builder
    public static class SpanStatus {
        private boolean error;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class MetricRequest {
        private String name;
        private MetricType type;
        private double value;
        private Tags tags;
        
        public enum MetricType {
            COUNTER,
            GAUGE,
            TIMER,
            HISTOGRAM
        }
    }

    @Data
    @Builder
    public static class AccessLogRequest {
        private String serviceName;
        private String method;
        private String path;
        private int statusCode;
        private long responseTime;
        private String clientIp;
        private String userAgent;
        private String traceId;
    }

    @Data
    @Builder
    public static class AccessLogEntry {
        private Instant timestamp;
        private String serviceName;
        private String method;
        private String path;
        private int statusCode;
        private long responseTime;
        private String clientIp;
        private String userAgent;
        private String traceId;
    }

    @Data
    @Builder
    public static class ServiceMetricsSnapshot {
        private String serviceName;
        private long requestCount;
        private long errorCount;
        private double averageResponseTime;
        private double p95ResponseTime;
        private double p99ResponseTime;
        private long activeRequests;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class TraceInfo {
        private String traceId;
        private int spanCount;
        private List<String> services;
        private Instant startTime;
    }

    @Data
    @Builder
    public static class MetricsExport {
        private Instant timestamp;
        private Map<String, Double> metrics;
    }
}