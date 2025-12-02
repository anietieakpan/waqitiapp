package com.waqiti.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready OpenTelemetry distributed tracing configuration.
 * 
 * Features:
 * - OTLP gRPC exporter for traces
 * - W3C Trace Context propagation
 * - Sampling strategies (always, probability, rate-limiting)
 * - Batch span processing for performance
 * - Service resource attributes
 * - Integration with Micrometer
 * 
 * Supports:
 * - Jaeger
 * - Zipkin
 * - New Relic
 * - Datadog
 * - Any OTLP-compatible backend
 */
@Configuration
@ConditionalOnProperty(name = "tracing.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class OpenTelemetryTracingConfig {

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${tracing.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${tracing.sampling.probability:1.0}")
    private double samplingProbability;

    @Value("${tracing.batch.max-queue-size:2048}")
    private int maxQueueSize;

    @Value("${tracing.batch.max-export-batch-size:512}")
    private int maxExportBatchSize;

    @Value("${tracing.batch.schedule-delay-millis:5000}")
    private long scheduleDelayMillis;

    @Value("${tracing.batch.export-timeout-millis:30000}")
    private long exportTimeoutMillis;

    @Value("${spring.profiles.active:unknown}")
    private String environment;

    @Value("${tracing.service.version:1.0.0}")
    private String serviceVersion;

    @Value("${tracing.service.namespace:waqiti}")
    private String serviceNamespace;

    /**
     * Configure OpenTelemetry SDK with OTLP exporter
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("Initializing OpenTelemetry tracing: service={}, endpoint={}, sampling={}",
                serviceName, otlpEndpoint, samplingProbability);

        // Create OTLP gRPC span exporter
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(exportTimeoutMillis, TimeUnit.MILLISECONDS)
                .build();

        // Create resource with service attributes
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                        .put(ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace)
                        .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                        .build()));

        // Configure sampler based on sampling probability
        Sampler sampler = samplingProbability >= 1.0 
                ? Sampler.alwaysOn()
                : Sampler.traceIdRatioBased(samplingProbability);

        // Create batch span processor for efficient span export
        BatchSpanProcessor batchSpanProcessor = BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(maxQueueSize)
                .setMaxExportBatchSize(maxExportBatchSize)
                .setScheduleDelay(Duration.ofMillis(scheduleDelayMillis))
                .setExporterTimeout(Duration.ofMillis(exportTimeoutMillis))
                .build();

        // Build SDK tracer provider
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler)
                .addSpanProcessor(batchSpanProcessor)
                .build();

        // Build OpenTelemetry SDK
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OpenTelemetry SDK");
            sdkTracerProvider.close();
        }));

        log.info("OpenTelemetry tracing initialized successfully");
        return openTelemetry;
    }

    /**
     * Custom span attributes for DLQ operations
     */
    @Bean
    public DlqTracingHelper dlqTracingHelper(OpenTelemetry openTelemetry) {
        return new DlqTracingHelper(openTelemetry);
    }

    /**
     * Helper class for adding DLQ-specific trace attributes
     */
    public static class DlqTracingHelper {
        private final io.opentelemetry.api.trace.Tracer tracer;

        public DlqTracingHelper(OpenTelemetry openTelemetry) {
            this.tracer = openTelemetry.getTracer("waqiti-dlq", "1.0.0");
        }

        /**
         * Start a span for DLQ message processing
         */
        public io.opentelemetry.api.trace.Span startDlqProcessingSpan(
                String topic, String eventId, String correlationId) {
            return tracer.spanBuilder("dlq.process")
                    .setAttribute("messaging.system", "kafka")
                    .setAttribute("messaging.destination", topic)
                    .setAttribute("messaging.message_id", eventId)
                    .setAttribute("messaging.correlation_id", correlationId)
                    .setAttribute("messaging.operation", "process")
                    .startSpan();
        }

        /**
         * Start a span for incident creation
         */
        public io.opentelemetry.api.trace.Span startIncidentCreationSpan(
                String incidentType, String priority) {
            return tracer.spanBuilder("incident.create")
                    .setAttribute("incident.type", incidentType)
                    .setAttribute("incident.priority", priority)
                    .startSpan();
        }

        /**
         * Start a span for notification sending
         */
        public io.opentelemetry.api.trace.Span startNotificationSpan(
                String channel, String notificationType) {
            return tracer.spanBuilder("notification.send")
                    .setAttribute("notification.channel", channel)
                    .setAttribute("notification.type", notificationType)
                    .startSpan();
        }

        /**
         * Start a span for escalation
         */
        public io.opentelemetry.api.trace.Span startEscalationSpan(
                String escalationType, String priority) {
            return tracer.spanBuilder("escalation.trigger")
                    .setAttribute("escalation.type", escalationType)
                    .setAttribute("escalation.priority", priority)
                    .startSpan();
        }

        /**
         * Add error to span
         */
        public void recordError(io.opentelemetry.api.trace.Span span, Throwable throwable) {
            span.recordException(throwable);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, throwable.getMessage());
        }

        /**
         * Add custom attributes to current span
         */
        public void addAttribute(io.opentelemetry.api.trace.Span span, String key, String value) {
            span.setAttribute(key, value);
        }

        /**
         * Add custom attributes to current span
         */
        public void addAttribute(io.opentelemetry.api.trace.Span span, String key, long value) {
            span.setAttribute(key, value);
        }

        /**
         * PRODUCTION FIX: Add boolean attribute to span
         * Used by SystemAlertsDlqConsumer for alert.recovered flag
         */
        public void addAttribute(io.opentelemetry.api.trace.Span span, String key, boolean value) {
            span.setAttribute(key, value);
        }

        /**
         * Complete span with success status
         */
        public void completeSpan(io.opentelemetry.api.trace.Span span) {
            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            span.end();
        }
    }
}
