package com.waqiti.common.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced Distributed Tracing Service with OpenTelemetry
 * Provides comprehensive tracing capabilities with support for multiple backends
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "waqiti.tracing.opentelemetry.enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryTracingService {

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;
    
    @Value("${waqiti.tracing.opentelemetry.endpoint:http://localhost:14250}")
    private String tracingEndpoint;
    
    @Value("${waqiti.tracing.opentelemetry.backend:jaeger}")
    private String tracingBackend; // jaeger, zipkin, otlp
    
    @Value("${waqiti.tracing.opentelemetry.sampling.strategy:adaptive}")
    private String samplingStrategy; // always, never, probabilistic, adaptive, rate_limiting
    
    @Value("${waqiti.tracing.opentelemetry.sampling.rate:0.1}")
    private double samplingRate;
    
    @Value("${waqiti.tracing.opentelemetry.export.batch.size:512}")
    private int exportBatchSize;
    
    @Value("${waqiti.tracing.opentelemetry.export.timeout.seconds:30}")
    private int exportTimeoutSeconds;
    
    private OpenTelemetry openTelemetry;
    private io.opentelemetry.api.trace.Tracer tracer;
    private SdkTracerProvider tracerProvider;
    
    // Metrics tracking
    private final Map<String, SpanMetrics> spanMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalSpansCreated = new AtomicLong(0);
    private final AtomicLong totalSpansExported = new AtomicLong(0);
    
    // Adaptive sampling state
    private final AdaptiveSampler adaptiveSampler = new AdaptiveSampler();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing OpenTelemetry tracing for service: {} with backend: {}", 
            serviceName, tracingBackend);
        
        // Create resource with service information
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, serviceName,
                ResourceAttributes.SERVICE_VERSION, getServiceVersion(),
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, getEnvironment()
            )));
        
        // Create span exporter based on backend
        SpanExporter spanExporter = createSpanExporter();
        
        // Create tracer provider with batch processor
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(exportBatchSize)
                .setMaxExportBatchSize(exportBatchSize)
                .setScheduleDelay(Duration.ofSeconds(5))
                .setExporterTimeout(Duration.ofSeconds(exportTimeoutSeconds))
                .build())
            .setResource(resource)
            .setSampler(createSampler())
            .build();
        
        // Build OpenTelemetry instance
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal();
        
        // Get tracer
        tracer = openTelemetry.getTracer(serviceName, getServiceVersion());
        
        log.info("OpenTelemetry tracing initialized successfully");
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down OpenTelemetry tracing");
        
        if (tracerProvider != null) {
            tracerProvider.shutdown();
        }
        
        log.info("OpenTelemetry tracing shut down successfully");
    }
    
    /**
     * Start a new trace span
     */
    public SpanContext startSpan(String spanName, SpanKind spanKind, Map<String, String> attributes) {
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName)
            .setSpanKind(spanKind)
            .setAttribute("service.name", serviceName)
            .setAttribute("correlation.id", CorrelationContext.getCorrelationId());
        
        // Add custom attributes
        if (attributes != null) {
            attributes.forEach((key, value) -> 
                spanBuilder.setAttribute(key, value));
        }
        
        Span span = spanBuilder.startSpan();
        
        totalSpansCreated.incrementAndGet();
        updateMetrics(spanName, "created");
        
        log.debug("Started span: {} with trace ID: {}", spanName, span.getSpanContext().getTraceId());
        
        return new SpanContext(span, span.makeCurrent());
    }
    
    /**
     * Start a child span
     */
    public SpanContext startChildSpan(String spanName, SpanContext parent, Map<String, String> attributes) {
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName)
            .setParent(Context.current().with(parent.getSpan()))
            .setSpanKind(SpanKind.INTERNAL);
        
        if (attributes != null) {
            attributes.forEach((key, value) -> 
                spanBuilder.setAttribute(key, value));
        }
        
        Span span = spanBuilder.startSpan();
        
        updateMetrics(spanName, "child_created");
        
        return new SpanContext(span, span.makeCurrent());
    }
    
    /**
     * Add event to current span
     */
    public void addEvent(String eventName, Map<String, Object> attributes) {
        Span currentSpan = Span.current();
        
        if (attributes != null && !attributes.isEmpty()) {
            Attributes eventAttributes = buildAttributes(attributes);
            currentSpan.addEvent(eventName, eventAttributes);
        } else {
            currentSpan.addEvent(eventName);
        }
        
        log.debug("Added event: {} to current span", eventName);
    }
    
    /**
     * Record exception in current span
     */
    public void recordException(Throwable exception) {
        Span currentSpan = Span.current();
        currentSpan.recordException(exception);
        currentSpan.setStatus(StatusCode.ERROR, exception.getMessage());
        
        updateMetrics(currentSpan.toString(), "error");
        
        log.error("Recorded exception in span: {}", exception.getMessage(), exception);
    }
    
    /**
     * Set baggage for context propagation
     */
    public void setBaggage(String key, String value) {
        Baggage baggage = Baggage.current();
        BaggageBuilder builder = baggage.toBuilder();
        builder.put(key, value);
        builder.build().makeCurrent();
        
        log.debug("Set baggage: {}={}", key, value);
    }
    
    /**
     * Get baggage value
     */
    public String getBaggage(String key) {
        return Baggage.current().getEntryValue(key);
    }
    
    /**
     * Inject trace context for propagation
     */
    public void injectContext(Map<String, String> carrier) {
        openTelemetry.getPropagators().getTextMapPropagator()
            .inject(Context.current(), carrier, MapSetter.INSTANCE);
        
        log.debug("Injected trace context into carrier");
    }
    
    /**
     * Extract trace context from incoming request
     */
    public Context extractContext(Map<String, String> carrier) {
        Context context = openTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), carrier, MapGetter.INSTANCE);
        
        log.debug("Extracted trace context from carrier");
        
        return context;
    }
    
    /**
     * End span and export
     */
    public void endSpan(SpanContext spanContext) {
        if (spanContext != null) {
            Span span = spanContext.getSpan();
            
            // Close scope first
            if (spanContext.getScope() != null) {
                spanContext.getScope().close();
            }
            
            // End span
            span.end();
            
            totalSpansExported.incrementAndGet();
            updateMetrics(span.toString(), "exported");
            
            log.debug("Ended span: {}", span.getSpanContext().getSpanId());
        }
    }
    
    /**
     * Get current trace ID
     */
    public String getCurrentTraceId() {
        return Span.current().getSpanContext().getTraceId();
    }
    
    /**
     * Get current span ID
     */
    public String getCurrentSpanId() {
        return Span.current().getSpanContext().getSpanId();
    }
    
    /**
     * Get tracing metrics
     */
    public TracingMetrics getMetrics() {
        return TracingMetrics.builder()
            .totalSpansCreated(totalSpansCreated.get())
            .totalSpansExported(totalSpansExported.get())
            .spanMetrics(new HashMap<>(spanMetrics))
            .samplingRate(getCurrentSamplingRate())
            .build();
    }
    
    // Private helper methods
    
    private SpanExporter createSpanExporter() {
        return switch (tracingBackend.toLowerCase()) {
            case "jaeger" -> OtlpGrpcSpanExporter.builder()
                .setEndpoint(tracingEndpoint)
                .setTimeout(Duration.ofSeconds(exportTimeoutSeconds))
                .build();
                
            case "zipkin" -> ZipkinSpanExporter.builder()
                .setEndpoint(tracingEndpoint + "/api/v2/spans")
                .setReadTimeout(Duration.ofSeconds(exportTimeoutSeconds))
                .build();
                
            default -> {
                log.warn("Unknown tracing backend: {}, defaulting to Jaeger", tracingBackend);
                yield OtlpGrpcSpanExporter.builder()
                    .setEndpoint(tracingEndpoint)
                    .build();
            }
        };
    }
    
    private Sampler createSampler() {
        return switch (samplingStrategy.toLowerCase()) {
            case "always" -> Sampler.alwaysOn();
            case "never" -> Sampler.alwaysOff();
            case "probabilistic" -> Sampler.traceIdRatioBased(samplingRate);
            case "adaptive" -> adaptiveSampler;
            case "rate_limiting" -> new RateLimitingSampler(100); // 100 traces per second
            default -> Sampler.traceIdRatioBased(samplingRate);
        };
    }
    
    private Attributes buildAttributes(Map<String, Object> attributes) {
        var builder = Attributes.builder();
        
        attributes.forEach((key, value) -> {
            if (value instanceof String) {
                builder.put(AttributeKey.stringKey(key), (String) value);
            } else if (value instanceof Long) {
                builder.put(AttributeKey.longKey(key), (Long) value);
            } else if (value instanceof Double) {
                builder.put(AttributeKey.doubleKey(key), (Double) value);
            } else if (value instanceof Boolean) {
                builder.put(AttributeKey.booleanKey(key), (Boolean) value);
            } else {
                builder.put(AttributeKey.stringKey(key), value.toString());
            }
        });
        
        return builder.build();
    }
    
    private void updateMetrics(String spanName, String operation) {
        SpanMetrics metrics = spanMetrics.computeIfAbsent(spanName, 
            k -> new SpanMetrics(spanName));
        
        switch (operation) {
            case "created" -> metrics.incrementCreated();
            case "child_created" -> metrics.incrementChildCreated();
            case "exported" -> metrics.incrementExported();
            case "error" -> metrics.incrementErrors();
        }
    }
    
    private double getCurrentSamplingRate() {
        if (samplingStrategy.equals("adaptive")) {
            return adaptiveSampler.getCurrentRate();
        }
        return samplingRate;
    }
    
    private String getServiceVersion() {
        return getClass().getPackage().getImplementationVersion() != null 
            ? getClass().getPackage().getImplementationVersion() 
            : "1.0.0";
    }
    
    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "default");
    }
    
    // Inner classes
    
    public static class SpanContext {
        private final Span span;
        private final Scope scope;
        
        public SpanContext(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }
        
        public Span getSpan() { return span; }
        public Scope getScope() { return scope; }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TracingMetrics {
        private long totalSpansCreated;
        private long totalSpansExported;
        private Map<String, SpanMetrics> spanMetrics;
        private double samplingRate;
    }
    
    public static class SpanMetrics {
        private final String spanName;
        private final AtomicLong created = new AtomicLong(0);
        private final AtomicLong childCreated = new AtomicLong(0);
        private final AtomicLong exported = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);
        
        public SpanMetrics(String spanName) {
            this.spanName = spanName;
        }
        
        public void incrementCreated() { created.incrementAndGet(); }
        public void incrementChildCreated() { childCreated.incrementAndGet(); }
        public void incrementExported() { exported.incrementAndGet(); }
        public void incrementErrors() { errors.incrementAndGet(); }
        
        public String getSpanName() { return spanName; }
        public long getCreated() { return created.get(); }
        public long getChildCreated() { return childCreated.get(); }
        public long getExported() { return exported.get(); }
        public long getErrors() { return errors.get(); }
    }
    
    /**
     * Adaptive sampler that adjusts sampling rate based on traffic
     */
    private static class AdaptiveSampler implements Sampler {
        private volatile double currentRate = 0.1;
        private final AtomicLong requestCount = new AtomicLong(0);
        private long lastAdjustmentTime = System.currentTimeMillis();

        // SECURITY FIX: Use SecureRandom instead of Math.random()
        private static final SecureRandom secureRandom = new SecureRandom();
        
        @Override
        public SamplingResult shouldSample(
                Context parentContext,
                String traceId,
                String name,
                SpanKind spanKind,
                Attributes attributes,
                List<LinkData> parentLinks) {
            
            long count = requestCount.incrementAndGet();
            
            // Adjust rate every 10000 requests or every minute
            if (count % 10000 == 0 || 
                System.currentTimeMillis() - lastAdjustmentTime > 60000) {
                adjustSamplingRate();
            }
            
            // Sample based on current rate
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            return secureRandom.nextDouble() < currentRate
                ? SamplingResult.recordAndSample()
                : SamplingResult.drop();
        }
        
        @Override
        public String getDescription() {
            return "AdaptiveSampler{rate=" + currentRate + "}";
        }
        
        private void adjustSamplingRate() {
            long requestsPerMinute = requestCount.get() * 60000 / 
                (System.currentTimeMillis() - lastAdjustmentTime);
            
            // Adjust rate based on traffic
            if (requestsPerMinute > 100000) {
                currentRate = Math.max(0.001, currentRate * 0.9); // Reduce sampling for high traffic
            } else if (requestsPerMinute < 1000) {
                currentRate = Math.min(1.0, currentRate * 1.1); // Increase sampling for low traffic
            }
            
            lastAdjustmentTime = System.currentTimeMillis();
            requestCount.set(0);
        }
        
        public double getCurrentRate() {
            return currentRate;
        }
    }
    
    /**
     * Rate limiting sampler
     */
    private static class RateLimitingSampler implements Sampler {
        private final int maxTracesPerSecond;
        private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong traceCount = new AtomicLong(0);
        
        public RateLimitingSampler(int maxTracesPerSecond) {
            this.maxTracesPerSecond = maxTracesPerSecond;
        }
        
        @Override
        public SamplingResult shouldSample(
                Context parentContext,
                String traceId,
                String name,
                SpanKind spanKind,
                Attributes attributes,
                List<LinkData> parentLinks) {
            
            long currentTime = System.currentTimeMillis();
            long lastReset = lastResetTime.get();
            
            // Reset counter every second
            if (currentTime - lastReset > 1000) {
                lastResetTime.compareAndSet(lastReset, currentTime);
                traceCount.set(0);
            }
            
            // Check rate limit
            if (traceCount.incrementAndGet() <= maxTracesPerSecond) {
                return SamplingResult.recordAndSample();
            }
            
            return SamplingResult.drop();
        }
        
        @Override
        public String getDescription() {
            return "RateLimitingSampler{maxTracesPerSecond=" + maxTracesPerSecond + "}";
        }
    }
    
    /**
     * TextMap setter for context propagation
     */
    private static class MapSetter implements TextMapSetter<Map<String, String>> {
        public static final MapSetter INSTANCE = new MapSetter();
        
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            if (carrier != null) {
                carrier.put(key, value);
            }
        }
    }
    
    /**
     * TextMap getter for context extraction
     */
    private static class MapGetter implements TextMapGetter<Map<String, String>> {
        public static final MapGetter INSTANCE = new MapGetter();
        
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier != null ? carrier.keySet() : Collections.emptySet();
        }
        
        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier != null ? carrier.get(key) : null;
        }
    }
}