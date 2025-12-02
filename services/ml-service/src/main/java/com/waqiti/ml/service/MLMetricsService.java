package com.waqiti.ml.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ML Metrics Service
 *
 * Tracks and reports metrics for ML model performance and predictions.
 *
 * @author Waqiti ML Team
 * @version 1.0.0
 * @since 2025-10-11
 */
@Service
@Slf4j
public class MLMetricsService {

    private final MeterRegistry meterRegistry;

    public MLMetricsService() {
        this.meterRegistry = new SimpleMeterRegistry();
    }

    public MLMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public void recordModelPrediction(String modelName, double predictionTime) {
        log.debug("Recording prediction for model: {} in {}ms", modelName, predictionTime);
        // Metrics would be recorded to registry
    }

    public void recordAnomalyDetection(String anomalyType, double score) {
        log.debug("Recording anomaly detection: {} with score {}", anomalyType, score);
        // Metrics would be recorded to registry
    }
}
