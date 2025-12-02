package com.waqiti.chaos.metrics;

import com.waqiti.chaos.core.ChaosResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChaosMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Timer.Sample> activeTimers = new ConcurrentHashMap<>();
    
    public void recordExperiment(ChaosResult result) {
        // Record experiment execution
        Counter.builder("chaos.experiments.total")
            .tag("experiment", result.getExperimentName())
            .tag("status", result.isSuccess() ? "success" : "failure")
            .register(meterRegistry)
            .increment();
        
        // Record duration
        if (result.getDuration() != null) {
            Timer.builder("chaos.experiments.duration")
                .tag("experiment", result.getExperimentName())
                .register(meterRegistry)
                .record(result.getDuration());
        }
        
        // Record custom metrics
        if (result.getMetrics() != null) {
            result.getMetrics().forEach((key, value) -> {
                if (value instanceof Number) {
                    meterRegistry.gauge(
                        "chaos.experiment.metric",
                        Map.of("experiment", result.getExperimentName(), "metric", key),
                        ((Number) value).doubleValue()
                    );
                }
            });
        }
        
        log.info("Recorded chaos experiment metrics: {}", result.getExperimentName());
    }
    
    public void startLatencyMeasurement(String service) {
        Timer.Sample sample = Timer.start(meterRegistry);
        activeTimers.put("latency_" + service, sample);
    }
    
    public void recordLatencyImpact(String service, long durationMs) {
        Timer.Sample sample = activeTimers.remove("latency_" + service);
        if (sample != null) {
            sample.stop(Timer.builder("chaos.network.latency")
                .tag("service", service)
                .register(meterRegistry));
        }
        
        meterRegistry.gauge("chaos.network.latency.impact", 
            Map.of("service", service), 
            durationMs);
    }
    
    public void recordErrorRate(String service, double errorRate) {
        meterRegistry.gauge("chaos.service.error_rate",
            Map.of("service", service),
            errorRate);
    }
    
    public void recordThroughput(String service, double throughput) {
        meterRegistry.gauge("chaos.service.throughput",
            Map.of("service", service, "unit", "bytes_per_second"),
            throughput);
    }
    
    public void recordRecoveryTime(String service, long recoveryTimeMs) {
        Timer.builder("chaos.service.recovery_time")
            .tag("service", service)
            .register(meterRegistry)
            .record(recoveryTimeMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordPodKillEvent(String service) {
        Counter.builder("chaos.kubernetes.pods_killed")
            .tag("service", service)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordDatabaseConnectionFailure(String operation) {
        Counter.builder("chaos.database.connection_failures")
            .tag("operation", operation)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordCircuitBreakerState(String service, String state) {
        meterRegistry.gauge("chaos.circuit_breaker.state",
            Map.of("service", service, "state", state),
            "OPEN".equals(state) ? 1 : 0);
    }
    
    public void recordSystemResourceUsage(String resource, double usage) {
        meterRegistry.gauge("chaos.system.resource_usage",
            Map.of("resource", resource),
            usage);
    }
    
    public void recordDataLoss(String experiment, long dataLostBytes) {
        Counter.builder("chaos.data.loss_bytes")
            .tag("experiment", experiment)
            .register(meterRegistry)
            .increment(dataLostBytes);
    }
    
    public void recordServiceAvailability(String service, double availabilityPercentage) {
        meterRegistry.gauge("chaos.service.availability",
            Map.of("service", service),
            availabilityPercentage);
    }
}