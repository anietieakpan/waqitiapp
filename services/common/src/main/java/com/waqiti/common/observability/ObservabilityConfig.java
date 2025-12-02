package com.waqiti.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Comprehensive observability configuration for Waqiti platform
 * Integrates metrics, tracing, and logging with industry-standard tools
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "waqiti.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityConfig {

    @Value("${waqiti.observability.jaeger.endpoint:http://localhost:14250}")
    private String jaegerEndpoint;

    @Value("${waqiti.observability.prometheus.port:9464}")
    private int prometheusPort;

    @Value("${waqiti.observability.sampling.ratio:0.1}")
    private double samplingRatio;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Configure OpenTelemetry SDK with Jaeger exporter
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public OpenTelemetry openTelemetry() {
        log.info("Configuring OpenTelemetry with OTLP endpoint: {}", jaegerEndpoint);

        // Configure OTLP span exporter
        OtlpGrpcSpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(jaegerEndpoint)
                .build();

        // Configure tracer provider with batch span processor
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(otlpExporter)
                        .setMaxExportBatchSize(512)
                        .setScheduleDelay(Duration.ofMillis(500))
                        .build())
                .setResource(io.opentelemetry.sdk.resources.Resource.getDefault()
                        .merge(io.opentelemetry.sdk.resources.Resource.builder()
                                .put(io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME, serviceName)
                                .put(io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_VERSION, "1.0.0")
                                .build()))
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    /**
     * Configure Prometheus metrics registry
     */
    @Bean
    @ConditionalOnClass(PrometheusMeterRegistry.class)
    @ConditionalOnProperty(name = "waqiti.observability.metrics.prometheus.enabled", havingValue = "true", matchIfMissing = true)
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        log.info("Configuring Prometheus metrics registry");
        
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
    
    /**
     * Configure Performance Metrics Registry
     */
    @Bean
    public PerformanceMetricsRegistry performanceMetricsRegistry(MeterRegistry meterRegistry) {
        return new PerformanceMetricsRegistry(meterRegistry);
    }
    
    /**
     * Configure Distributed Tracing
     * @param tracer Micrometer tracer for distributed tracing
     * @return DistributedTracingConfig bean
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public DistributedTracingConfig distributedTracingConfig(Tracer tracer) {
        log.info("Configuring DistributedTracingConfig with tracer");
        return new DistributedTracingConfig(tracer);
    }

    /**
     * Customize meter registry with Waqiti-specific settings
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
        return registry -> {
            log.info("Customizing meter registry for service: {}", serviceName);
            
            // Add common tags to all metrics
            registry.config()
                    .commonTags(
                            "application", serviceName,
                            "environment", System.getProperty("spring.profiles.active", "default"),
                            "version", "1.0.0"
                    )
                    // Configure histogram buckets for better percentile accuracy (API changed in newer versions)
                    .meterFilter(MeterFilter.deny(id -> false)) // maximumExpectedValue method changed
                    
                    // Configure percentiles for important timers
                    .meterFilter(MeterFilter.replaceTagValues("uri", uri -> {
                        // Sanitize URIs to reduce cardinality
                        if (uri.matches(".*\\d+.*")) {
                            return uri.replaceAll("\\d+", "{id}");
                        }
                        return uri;
                    }))
                    
                    // Configure distribution statistics
                    .meterFilter(new MeterFilter() {
                        @Override
                        public DistributionStatisticConfig configure(io.micrometer.core.instrument.Meter.Id id,
                                                                   DistributionStatisticConfig config) {
                            if (id.getName().startsWith("waqiti.payments")) {
                                return DistributionStatisticConfig.builder()
                                        .percentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                                        .percentilesHistogram(true)
                                        .build()
                                        .merge(config);
                            }
                            if (id.getName().startsWith("waqiti.database")) {
                                return DistributionStatisticConfig.builder()
                                        .percentiles(0.5, 0.9, 0.95, 0.99)
                                        .percentilesHistogram(true)
                                        .build()
                                        .merge(config);
                            }
                            return config;
                        }
                    })
                    
                    // Deny certain noisy metrics
                    .meterFilter(MeterFilter.deny(id -> 
                            id.getName().startsWith("jvm.gc.pause") && 
                            id.getTag("cause") != null && 
                            id.getTag("cause").contains("Metadata")
                    ));
        };
    }

    /**
     * OpenTelemetry tracer bean for manual instrumentation
     * Note: This returns OpenTelemetry's Tracer, not Micrometer's
     */
    @Bean
    public io.opentelemetry.api.trace.Tracer openTelemetryTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName);
    }

    /**
     * Business metrics configuration
     */
    @Bean
    public BusinessMetricsRegistry businessMetricsRegistry(MeterRegistry meterRegistry) {
        return new BusinessMetricsRegistry(meterRegistry, serviceName);
    }

    /**
     * Security metrics configuration
     */
    @Bean
    public SecurityMetricsRegistry securityMetricsRegistry(MeterRegistry meterRegistry) {
        return new SecurityMetricsRegistry(meterRegistry, serviceName);
    }


    /**
     * Financial transaction tracing service
     * @param tracer Micrometer tracer for distributed tracing
     * @param correlationIdService Service for correlation ID management
     * @param businessMetrics Business metrics registry
     * @return FinancialTransactionTracing bean
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.observability.financial-tracing.enabled", havingValue = "true", matchIfMissing = true)
    public FinancialTransactionTracing financialTransactionTracing(
            io.micrometer.tracing.Tracer tracer,
            com.waqiti.common.correlation.CorrelationIdService correlationIdService,
            BusinessMetricsRegistry businessMetrics) {
        log.info("Configuring FinancialTransactionTracing with Micrometer tracer");
        return new FinancialTransactionTracing(tracer, correlationIdService, businessMetrics);
    }

    /**
     * Financial correlation interceptor
     * @param correlationIdService Service for correlation ID management
     * @param financialTracing Financial transaction tracing service
     * @param distributedTracingConfig Distributed tracing configuration
     * @return FinancialCorrelationInterceptor bean
     */
    @Bean
    public FinancialCorrelationInterceptor financialCorrelationInterceptor(
            com.waqiti.common.correlation.CorrelationIdService correlationIdService,
            FinancialTransactionTracing financialTracing,
            DistributedTracingConfig distributedTracingConfig) {
        log.info("Configuring FinancialCorrelationInterceptor");
        return new FinancialCorrelationInterceptor(correlationIdService, financialTracing, distributedTracingConfig);
    }
    
    /**
     * Financial transaction tracing aspect
     */
    @Bean
    public FinancialTransactionTracingAspect financialTransactionTracingAspect(
            FinancialTransactionTracing financialTracing) {
        return new FinancialTransactionTracingAspect(financialTracing);
    }

    /**
     * Custom span processor for financial transactions
     */
    @Bean
    public FinancialTransactionSpanProcessor financialSpanProcessor() {
        return new FinancialTransactionSpanProcessor();
    }

    /**
     * Log correlation configuration
     */
    @Bean
    public LogCorrelationConfig logCorrelationConfig() {
        return LogCorrelationConfig.builder()
                .includeTraceId(true)
                .includeSpanId(true)
                .includeUserId(true)
                .includeSessionId(true)
                .build();
    }

    /**
     * SLA monitoring configuration
     */
    @Bean
    public SLAMonitoringConfig slaMonitoringConfig() {
        return SLAMonitoringConfig.builder()
                .paymentProcessingSLO(Duration.ofSeconds(5))
                .userRegistrationSLO(Duration.ofSeconds(3))
                .databaseQuerySLO(Duration.ofMillis(500))
                .apiResponseSLO(Duration.ofSeconds(2))
                .availabilityTarget(99.9)
                .build();
    }

    /**
     * Error tracking configuration
     */
    @Bean
    public ErrorTrackingConfig errorTrackingConfig() {
        return ErrorTrackingConfig.builder()
                .trackBusinessErrors(true)
                .trackSystemErrors(true)
                .trackSecurityEvents(true)
                .includeStackTrace(true)
                .includeRequestContext(true)
                .errorThreshold(0.01) // 1% error rate threshold
                .build();
    }
}