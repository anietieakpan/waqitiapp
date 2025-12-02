package com.waqiti.common.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.instrumentation.jdbc.datasource.OpenTelemetryDataSource;

// Waqiti custom implementations
import com.waqiti.common.telemetry.exporter.WaqitiJaegerSpanExporter;
import com.waqiti.common.telemetry.exporter.WaqitiPrometheusMetricsExporter;
import com.waqiti.common.telemetry.kafka.WaqitiKafkaTracingProducerInterceptor;
import com.waqiti.common.telemetry.kafka.WaqitiKafkaTracingConsumerInterceptor;
import io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive OpenTelemetry Configuration for Distributed Tracing
 * 
 * This configuration provides enterprise-grade distributed tracing capabilities:
 * - Multiple exporter support (Jaeger, Zipkin, OTLP)
 * - Automatic instrumentation for Spring, JDBC, Kafka, Redis
 * - Custom span attributes and baggage propagation
 * - Sampling strategies for production efficiency
 * - Context propagation across service boundaries
 * - Performance metrics collection
 * - Error tracking and alerting
 * 
 * Trace Collection Strategy:
 * 1. ALWAYS sample critical financial transactions
 * 2. PROBABILISTIC sampling for regular requests (configurable rate)
 * 3. NEVER sample health checks and metrics endpoints
 * 4. ADAPTIVE sampling based on error rates
 * 
 * Integration Points:
 * - HTTP: Automatic instrumentation with W3C trace context
 * - Kafka: Producer/Consumer interceptors for async tracing
 * - Database: JDBC wrapper for query tracing
 * - Redis: Command interception for cache tracing
 * - gRPC: Client/Server interceptors for RPC tracing
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OpenTelemetryProperties.class)
public class OpenTelemetryConfiguration implements WebMvcConfigurer {
    
    @Autowired
    private Environment environment;
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${opentelemetry.exporter.type:otlp}")
    private String exporterType;
    
    @Value("${opentelemetry.exporter.endpoint:http://localhost:4317}")
    private String exporterEndpoint;
    
    @Value("${opentelemetry.sampling.probability:1.0}")
    private double samplingProbability;
    
    @Value("${opentelemetry.batch.delay.millis:5000}")
    private long batchDelayMillis;
    
    @Value("${opentelemetry.batch.max.queue.size:2048}")
    private int maxQueueSize;
    
    @Value("${opentelemetry.batch.max.export.batch.size:512}")
    private int maxExportBatchSize;
    
    @Value("${opentelemetry.metrics.enabled:true}")
    private boolean metricsEnabled;
    
    @Value("${opentelemetry.logs.enabled:false}")
    private boolean logsEnabled;
    
    /**
     * Create Resource with service information
     */
    @Bean
    public Resource openTelemetryResource() {
        String environment = this.environment.getActiveProfiles().length > 0 ? 
            this.environment.getActiveProfiles()[0] : "default";
        
        return Resource.getDefault()
            .merge(Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, getServiceVersion())
                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                .put(ResourceAttributes.HOST_NAME, getHostName())
                .put(ResourceAttributes.PROCESS_PID, ProcessHandle.current().pid())
                .put(ResourceAttributes.TELEMETRY_SDK_LANGUAGE, "java")
                .put(ResourceAttributes.TELEMETRY_SDK_NAME, "opentelemetry")
                .put(ResourceAttributes.TELEMETRY_SDK_VERSION, "1.42.1")
                .put("service.namespace", "waqiti")
                .put("service.layer", determineServiceLayer())
                .put("service.type", determineServiceType())
                .build());
    }
    
    /**
     * Create Span Exporter based on configuration
     */
    @Bean
    public SpanExporter spanExporter() {
        switch (exporterType.toLowerCase()) {
            case "jaeger":
                return createJaegerExporter();
            case "zipkin":
                return createZipkinExporter();
            case "otlp":
            default:
                return createOtlpExporter();
        }
    }
    
    /**
     * Create OTLP (OpenTelemetry Protocol) Exporter
     */
    private SpanExporter createOtlpExporter() {
        log.info("Configuring OTLP span exporter. Endpoint: {}", exporterEndpoint);
        
        return OtlpGrpcSpanExporter.builder()
            .setEndpoint(exporterEndpoint)
            .setTimeout(Duration.ofSeconds(10))
            .setCompression("gzip")
            .setHeaders(Map.of(
                "service.name", serviceName,
                "api.key", getApiKey()
            ))
            .build();
    }
    
    /**
     * Create Jaeger Exporter using Waqiti implementation
     */
    private SpanExporter createJaegerExporter() {
        log.info("Configuring Waqiti Jaeger span exporter. Endpoint: {}", exporterEndpoint);
        
        return WaqitiJaegerSpanExporter.create(exporterEndpoint, Duration.ofSeconds(10));
    }
    
    /**
     * Create Zipkin Exporter
     */
    private SpanExporter createZipkinExporter() {
        log.info("Configuring Zipkin span exporter. Endpoint: {}", exporterEndpoint);
        
        return ZipkinSpanExporter.builder()
            .setEndpoint(exporterEndpoint + "/api/v2/spans")
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Create custom sampling strategy
     */
    @Bean
    public Sampler sampler() {
        return new WaqitiCustomSampler(samplingProbability);
    }
    
    /**
     * Create Span Processor with batching
     */
    @Bean
    public SpanProcessor spanProcessor(SpanExporter spanExporter) {
        return BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(batchDelayMillis, TimeUnit.MILLISECONDS)
            .setMaxQueueSize(maxQueueSize)
            .setMaxExportBatchSize(maxExportBatchSize)
            .setExporterTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Create SdkTracerProvider with processors
     */
    @Bean
    public SdkTracerProvider sdkTracerProvider(Resource resource, 
                                               SpanProcessor spanProcessor,
                                               Sampler sampler) {
        SdkTracerProvider.Builder builder = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(sampler)
            .addSpanProcessor(spanProcessor);
        
        // Add additional processors for critical spans
        builder.addSpanProcessor(new CriticalSpanProcessor());
        
        // Add error tracking processor
        builder.addSpanProcessor(new ErrorTrackingSpanProcessor());
        
        // Add performance monitoring processor
        builder.addSpanProcessor(new PerformanceMonitoringSpanProcessor());
        
        SdkTracerProvider tracerProvider = builder.build();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OpenTelemetry tracer provider");
            tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }));
        
        return tracerProvider;
    }
    
    /**
     * Create Context Propagators for trace context propagation
     */
    @Bean
    public ContextPropagators contextPropagators() {
        return ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance(),
                new WaqitiCustomPropagator()
            )
        );
    }
    
    /**
     * Create OpenTelemetry SDK instance
     */
    @Bean
    @Primary
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider,
                                       ContextPropagators contextPropagators) {
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(contextPropagators)
            .build();
        
        // Register as global
        io.opentelemetry.api.GlobalOpenTelemetry.set(openTelemetry);
        
        log.info("OpenTelemetry SDK initialized for service: {}", serviceName);
        
        return openTelemetry;
    }
    
    /**
     * Create Tracer for manual instrumentation
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, getServiceVersion());
    }
    
    /**
     * Create instrumented DataSource for database tracing
     */
    @Bean
    @ConditionalOnProperty(value = "opentelemetry.jdbc.enabled", havingValue = "true", matchIfMissing = true)
    public DataSourceInstrumentationBean dataSourceInstrumentation() {
        return new DataSourceInstrumentationBean();
    }
    
    /**
     * Configure Spring WebMVC instrumentation
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        OpenTelemetry openTelemetry = io.opentelemetry.api.GlobalOpenTelemetry.get();
        SpringWebMvcTelemetry telemetry = SpringWebMvcTelemetry.create(openTelemetry);
        registry.addInterceptor(new OpenTelemetryHandlerInterceptor(openTelemetry));
    }
    
    /**
     * Kafka Producer configuration for trace propagation using Waqiti implementation
     */
    @Bean
    @ConditionalOnProperty(value = "opentelemetry.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public Map<String, Object> kafkaProducerTracingConfig() {
        log.info("Configuring Kafka producer tracing with Waqiti interceptor");
        return Map.of(
            "interceptor.classes", WaqitiKafkaTracingProducerInterceptor.class.getName()
        );
    }
    
    /**
     * Kafka Consumer configuration for trace propagation using Waqiti implementation
     */
    @Bean
    @ConditionalOnProperty(value = "opentelemetry.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public Map<String, Object> kafkaConsumerTracingConfig() {
        log.info("Configuring Kafka consumer tracing with Waqiti interceptor");
        return Map.of(
            "interceptor.classes", WaqitiKafkaTracingConsumerInterceptor.class.getName()
        );
    }
    
    /**
     * Metrics configuration using Waqiti Prometheus exporter
     */
    @Bean
    @ConditionalOnProperty(value = "opentelemetry.metrics.enabled", havingValue = "true")
    public SdkMeterProvider meterProvider(Resource resource) {
        log.info("Configuring metrics with Waqiti Prometheus exporter on port 9090");
        
        WaqitiPrometheusMetricsExporter prometheusExporter = 
            WaqitiPrometheusMetricsExporter.create(9090, "/metrics", resource);
        
        return SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                PeriodicMetricReader.builder(prometheusExporter)
                    .setInterval(Duration.ofSeconds(30))
                    .build()
            )
            .build();
    }
    
    // Helper methods
    
    private String getServiceVersion() {
        return getClass().getPackage().getImplementationVersion() != null ?
            getClass().getPackage().getImplementationVersion() : "1.0.0";
    }
    
    private String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String determineServiceLayer() {
        if (serviceName.contains("gateway")) return "edge";
        if (serviceName.contains("service")) return "application";
        if (serviceName.contains("database")) return "data";
        return "unknown";
    }
    
    private String determineServiceType() {
        if (serviceName.contains("payment")) return "financial";
        if (serviceName.contains("compliance")) return "regulatory";
        if (serviceName.contains("user")) return "identity";
        if (serviceName.contains("notification")) return "messaging";
        return "general";
    }
    
    private String getApiKey() {
        return environment.getProperty("opentelemetry.api.key", "");
    }
    
    /**
     * Custom Span Processor for critical spans
     */
    private static class CriticalSpanProcessor implements SpanProcessor {
        @Override
        public void onStart(io.opentelemetry.context.Context parentContext, 
                          io.opentelemetry.sdk.trace.ReadWriteSpan span) {
            // Add critical span attributes
            if (isCriticalSpan(span)) {
                span.setAttribute("span.critical", true);
                span.setAttribute("span.priority", "high");
            }
        }
        
        @Override
        public boolean isStartRequired() {
            return true;
        }
        
        @Override
        public void onEnd(io.opentelemetry.sdk.trace.ReadableSpan span) {
            // Process critical spans
            if (span.getAttribute(io.opentelemetry.api.common.AttributeKey.booleanKey("span.critical")) == Boolean.TRUE) {
                // Could send to special processing or alerting
                log.debug("Critical span completed: {} in {}ms", 
                    span.getName(), span.getLatencyNanos() / 1_000_000);
            }
        }
        
        @Override
        public boolean isEndRequired() {
            return true;
        }
        
        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode forceFlush() {
            return CompletableResultCode.ofSuccess();
        }
        
        private boolean isCriticalSpan(io.opentelemetry.sdk.trace.ReadWriteSpan span) {
            String name = span.getName();
            return name.contains("payment") || 
                   name.contains("settlement") || 
                   name.contains("compliance") ||
                   name.contains("fraud");
        }
    }
    
    /**
     * Error Tracking Span Processor
     */
    private static class ErrorTrackingSpanProcessor implements SpanProcessor {
        @Override
        public void onStart(io.opentelemetry.context.Context parentContext, 
                          io.opentelemetry.sdk.trace.ReadWriteSpan span) {
            // No-op
        }
        
        @Override
        public boolean isStartRequired() {
            return false;
        }
        
        @Override
        public void onEnd(io.opentelemetry.sdk.trace.ReadableSpan span) {
            if (span.getStatus().getStatusCode() == io.opentelemetry.api.trace.StatusCode.ERROR) {
                log.error("Error in span: {} - {}", 
                    span.getName(), 
                    span.getStatus().getDescription());
                // Could trigger alerts or error tracking
            }
        }
        
        @Override
        public boolean isEndRequired() {
            return true;
        }
        
        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode forceFlush() {
            return CompletableResultCode.ofSuccess();
        }
    }
    
    /**
     * Performance Monitoring Span Processor
     */
    private static class PerformanceMonitoringSpanProcessor implements SpanProcessor {
        private static final long SLOW_SPAN_THRESHOLD_NANOS = 1_000_000_000L; // 1 second
        
        @Override
        public void onStart(io.opentelemetry.context.Context parentContext, 
                          io.opentelemetry.sdk.trace.ReadWriteSpan span) {
            // No-op
        }
        
        @Override
        public boolean isStartRequired() {
            return false;
        }
        
        @Override
        public void onEnd(io.opentelemetry.sdk.trace.ReadableSpan span) {
            long latencyNanos = span.getLatencyNanos();
            if (latencyNanos > SLOW_SPAN_THRESHOLD_NANOS) {
                log.warn("Slow span detected: {} took {}ms", 
                    span.getName(), 
                    latencyNanos / 1_000_000);
                // Could trigger performance alerts
            }
        }
        
        @Override
        public boolean isEndRequired() {
            return true;
        }
        
        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode forceFlush() {
            return CompletableResultCode.ofSuccess();
        }
    }
}