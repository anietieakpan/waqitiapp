package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Metrics Service
 *
 * Records and tracks metrics for alert resolutions and analytics processing.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record alert resolution metrics
     */
    public void recordAlertResolution(String alertType, String resolutionMethod,
                                     Long processingTimeMs, boolean success,
                                     String correlationId) {
        try {
            meterRegistry.counter("alert.resolution.total",
                    "type", alertType,
                    "method", resolutionMethod,
                    "success", String.valueOf(success)
            ).increment();

            if (processingTimeMs != null) {
                meterRegistry.timer("alert.resolution.duration",
                        "type", alertType
                ).record(java.time.Duration.ofMillis(processingTimeMs));
            }
        } catch (Exception e) {
            log.error("Failed to record metrics: correlationId={}", correlationId, e);
        }
    }
}
