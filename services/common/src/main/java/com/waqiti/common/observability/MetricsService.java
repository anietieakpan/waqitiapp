package com.waqiti.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Metrics Service for observability
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Increment a counter metric
     */
    public void incrementCounter(String metricName, String... tags) {
        Counter.builder(metricName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record a timer metric
     */
    public void recordTimer(String metricName, long durationMs, String... tags) {
        Timer.builder(metricName)
                .tags(tags)
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Record a gauge metric
     */
    public void recordGauge(String metricName, double value, String... tags) {
        io.micrometer.core.instrument.Gauge.builder(metricName, () -> value)
                .tags(tags)
                .register(meterRegistry);
    }
}
