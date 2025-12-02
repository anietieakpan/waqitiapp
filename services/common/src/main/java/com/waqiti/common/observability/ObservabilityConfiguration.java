package com.waqiti.common.observability;

import com.waqiti.common.servicemesh.ObservabilityManager;
import com.waqiti.common.tracing.OpenTelemetryTracingService;
import com.waqiti.common.tracing.TracingMetricsExporter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.kafka.KafkaConsumerMetrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for comprehensive observability setup
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ObservabilityConfiguration {

    private final Environment environment;
    
    @PostConstruct
    public void logObservabilitySetup() {
        log.info("Configuring comprehensive observability for Waqiti P2P Payment Platform");
    }
    
    /**
     * Configure meter registry with custom tags
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
        return registry -> {
            // Add application-wide common tags
            registry.config()
                .commonTags(
                    "application", getApplicationName(),
                    "version", getApplicationVersion(),
                    "environment", getEnvironment(),
                    "service.type", "financial",
                    "platform", "waqiti-p2p"
                );
                
            log.info("Configured meter registry with common tags");
        };
    }
    
    /**
     * Register JVM metrics
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }
    
    @Bean
    public JvmHeapPressureMetrics jvmHeapPressureMetrics() {
        return new JvmHeapPressureMetrics();
    }
    
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }
    
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }
    
    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }
    
    /**
     * Register system metrics
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }
    
    @Bean
    public FileDescriptorMetrics fileDescriptorMetrics() {
        return new FileDescriptorMetrics();
    }
    
    @Bean
    public io.micrometer.core.instrument.binder.system.DiskSpaceMetrics diskSpaceMetrics() {
        return new io.micrometer.core.instrument.binder.system.DiskSpaceMetrics(getLogDirectory());
    }
    
    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }
    
    /**
     * Register application-specific metrics
     */
    @Bean
    public LogbackMetrics logbackMetrics() {
        return new LogbackMetrics();
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public KafkaConsumerMetrics kafkaConsumerMetrics() {
        return new KafkaConsumerMetrics();
    }
    
    /**
     * Create OpenTelemetry tracer
     */
    @Bean
    public Tracer openTelemetryTracer() {
        return GlobalOpenTelemetry.getTracer("waqiti-p2p", getApplicationVersion());
    }
    
    /**
     * Create observability manager with proper configuration
     */
    @Bean
    public ObservabilityManager observabilityManager(MeterRegistry meterRegistry, Tracer tracer) {
        return ObservabilityManager.builder()
            .meterRegistry(meterRegistry)
            .tracer(tracer)
            .tracingEnabled(isTracingEnabled())
            .tracingEndpoint(getTracingEndpoint())
            .metricsEnabled(isMetricsEnabled())
            .metricsPort(getMetricsPort())
            .accessLogEnabled(isAccessLogEnabled())
            .distributedTracingEnabled(isDistributedTracingEnabled())
            .build();
    }
    
    /**
     * Create tracing service
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public OpenTelemetryTracingService openTelemetryTracingService() {
        return new OpenTelemetryTracingService();
    }
    
    /**
     * Create metrics exporter
     * @param distributedTracingService Distributed tracing service
     * @param openTelemetryService OpenTelemetry tracing service
     * @param meterRegistry Meter registry for metrics
     * @return TracingMetricsExporter bean
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.metrics.export.enabled", havingValue = "true", matchIfMissing = true)
    public TracingMetricsExporter tracingMetricsExporter(
            com.waqiti.common.tracing.DistributedTracingService distributedTracingService,
            com.waqiti.common.tracing.OpenTelemetryTracingService openTelemetryService,
            MeterRegistry meterRegistry) {
        int exportIntervalSeconds = 60; // Default 60 seconds export interval
        log.info("Creating TracingMetricsExporter with {} second interval", exportIntervalSeconds);
        return new TracingMetricsExporter(distributedTracingService, openTelemetryService, exportIntervalSeconds, meterRegistry);
    }
    
    /**
     * Create comprehensive observability coordinator
     */
    @Bean
    public ComprehensiveObservabilityCoordinator comprehensiveObservabilityCoordinator(
            MeterRegistry meterRegistry,
            OpenTelemetryTracingService tracingService,
            ObservabilityManager observabilityManager,
            TracingMetricsExporter metricsExporter,
            Tracer tracer) {
        
        return new ComprehensiveObservabilityCoordinator(
            meterRegistry, tracingService, observabilityManager, metricsExporter, tracer);
    }
    
    // Helper methods to read configuration
    
    private String getApplicationName() {
        return environment.getProperty("spring.application.name", "waqiti-service");
    }
    
    private String getApplicationVersion() {
        return environment.getProperty("application.version", "1.0.0");
    }
    
    private String getEnvironment() {
        return environment.getProperty("spring.profiles.active", "development");
    }
    
    private boolean isTracingEnabled() {
        return environment.getProperty("waqiti.tracing.enabled", Boolean.class, true);
    }
    
    private String getTracingEndpoint() {
        return environment.getProperty("waqiti.tracing.endpoint", "http://localhost:14268/api/traces");
    }
    
    private boolean isMetricsEnabled() {
        return environment.getProperty("waqiti.metrics.enabled", Boolean.class, true);
    }
    
    private int getMetricsPort() {
        return environment.getProperty("waqiti.metrics.port", Integer.class, 9090);
    }
    
    private boolean isAccessLogEnabled() {
        return environment.getProperty("waqiti.observability.access-log.enabled", Boolean.class, true);
    }
    
    private boolean isDistributedTracingEnabled() {
        return environment.getProperty("waqiti.tracing.distributed.enabled", Boolean.class, true);
    }
    
    private java.io.File getLogDirectory() {
        String logDir = environment.getProperty("logging.file.path", "/tmp/logs");
        return new java.io.File(logDir);
    }
}