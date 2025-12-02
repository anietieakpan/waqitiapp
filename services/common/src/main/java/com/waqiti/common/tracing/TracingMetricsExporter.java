package com.waqiti.common.tracing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exports tracing metrics to monitoring systems
 * Provides visibility into tracing system health and performance
 */
@Slf4j
@Component
public class TracingMetricsExporter {

    private final DistributedTracingService distributedTracingService;
    private final OpenTelemetryTracingService openTelemetryService;
    private final int exportIntervalSeconds;
    private final MeterRegistry meterRegistry;
    
    // Metric counters
    private final AtomicLong totalTracesExported = new AtomicLong(0);
    private final AtomicLong totalMetricsExported = new AtomicLong(0);
    private final AtomicLong exportErrors = new AtomicLong(0);
    
    public TracingMetricsExporter(
            DistributedTracingService distributedTracingService,
            OpenTelemetryTracingService openTelemetryService,
            int exportIntervalSeconds,
            MeterRegistry meterRegistry) {
        this.distributedTracingService = distributedTracingService;
        this.openTelemetryService = openTelemetryService;
        this.exportIntervalSeconds = exportIntervalSeconds;
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing tracing metrics exporter with interval: {} seconds", exportIntervalSeconds);
        
        if (meterRegistry != null) {
            registerMetrics();
        }
    }
    
    /**
     * Register custom metrics with Micrometer
     */
    private void registerMetrics() {
        // Register gauge metrics for trace statistics
        meterRegistry.gauge("tracing.active.traces", distributedTracingService, 
            service -> service.getTraceMetrics().size());
        
        meterRegistry.gauge("tracing.total.exported", this, 
            exporter -> exporter.totalTracesExported.get());
        
        meterRegistry.gauge("tracing.export.errors", this, 
            exporter -> exporter.exportErrors.get());
        
        log.info("Registered tracing metrics with Micrometer");
    }
    
    /**
     * Export metrics periodically
     */
    @Scheduled(fixedDelayString = "${waqiti.tracing.metrics.export-interval-seconds:60}000")
    public void exportMetrics() {
        try {
            log.debug("Starting tracing metrics export");
            
            // Export distributed tracing metrics
            exportDistributedTracingMetrics();
            
            // Export OpenTelemetry metrics
            if (openTelemetryService != null) {
                exportOpenTelemetryMetrics();
            }
            
            totalMetricsExported.incrementAndGet();
            
            log.debug("Completed tracing metrics export");
            
        } catch (Exception e) {
            log.error("Error exporting tracing metrics", e);
            exportErrors.incrementAndGet();
        }
    }
    
    /**
     * Export distributed tracing metrics
     */
    private void exportDistributedTracingMetrics() {
        Map<String, DistributedTracingService.TraceMetrics> traceMetrics = 
            distributedTracingService.getTraceMetrics();
        
        if (traceMetrics.isEmpty()) {
            log.debug("No trace metrics to export");
            return;
        }
        
        traceMetrics.forEach((operationName, metrics) -> {
            if (meterRegistry != null) {
                Tags tags = Tags.of("operation", operationName);
                
                // Record trace counts
                meterRegistry.counter("traces.started", tags)
                    .increment(metrics.getStarted());
                
                meterRegistry.counter("traces.completed", tags)
                    .increment(metrics.getCompleted());
                
                meterRegistry.counter("traces.errors", tags)
                    .increment(metrics.getErrors());
                
                // Record timing metrics
                if (metrics.getCompleted() > 0) {
                    meterRegistry.timer("traces.duration", tags)
                        .record(java.time.Duration.ofMillis((long) metrics.getAverageDurationMs()));
                    
                    meterRegistry.gauge("traces.duration.min", tags, metrics.getMinDurationMs());
                    meterRegistry.gauge("traces.duration.max", tags, metrics.getMaxDurationMs());
                    meterRegistry.gauge("traces.duration.avg", tags, metrics.getAverageDurationMs());
                }
            }
            
            // Log summary
            log.info("Trace metrics for {}: started={}, completed={}, errors={}, avg_duration={}ms",
                operationName, 
                metrics.getStarted(),
                metrics.getCompleted(),
                metrics.getErrors(),
                String.format("%.2f", metrics.getAverageDurationMs()));
        });
        
        totalTracesExported.addAndGet(traceMetrics.size());
        
        // Reset metrics after export
        distributedTracingService.resetMetrics();
    }
    
    /**
     * Export OpenTelemetry metrics
     */
    private void exportOpenTelemetryMetrics() {
        OpenTelemetryTracingService.TracingMetrics metrics = openTelemetryService.getMetrics();
        
        if (meterRegistry != null) {
            // Record OpenTelemetry metrics
            meterRegistry.gauge("opentelemetry.spans.created", metrics.getTotalSpansCreated());
            meterRegistry.gauge("opentelemetry.spans.exported", metrics.getTotalSpansExported());
            meterRegistry.gauge("opentelemetry.sampling.rate", metrics.getSamplingRate());
            
            // Record per-span metrics
            metrics.getSpanMetrics().forEach((spanName, spanMetrics) -> {
                Tags tags = Tags.of("span", spanName);
                
                meterRegistry.gauge("opentelemetry.span.created", tags, spanMetrics.getCreated());
                meterRegistry.gauge("opentelemetry.span.exported", tags, spanMetrics.getExported());
                meterRegistry.gauge("opentelemetry.span.errors", tags, spanMetrics.getErrors());
            });
        }
        
        // Log OpenTelemetry summary
        log.info("OpenTelemetry metrics: spans_created={}, spans_exported={}, sampling_rate={}",
            metrics.getTotalSpansCreated(),
            metrics.getTotalSpansExported(),
            String.format("%.3f", metrics.getSamplingRate()));
    }
    
    /**
     * Get export statistics
     */
    public ExportStatistics getStatistics() {
        return ExportStatistics.builder()
            .totalTracesExported(totalTracesExported.get())
            .totalMetricsExported(totalMetricsExported.get())
            .exportErrors(exportErrors.get())
            .currentTraceCount(distributedTracingService.getTraceMetrics().size())
            .build();
    }
    
    /**
     * Force immediate metric export
     */
    public void forceExport() {
        log.info("Forcing immediate metric export");
        exportMetrics();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ExportStatistics {
        private long totalTracesExported;
        private long totalMetricsExported;
        private long exportErrors;
        private int currentTraceCount;
    }
}