package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Metrics Service
 * Records security metrics and analytics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    /**
     * Record metric
     */
    public void recordMetric(String metricName, double value, Map<String, String> tags) {
        try {
            log.debug("Recording metric: {}={} tags={}", metricName, value, tags);

            // In production, this would integrate with:
            // - Prometheus
            // - CloudWatch
            // - Datadog
            // - New Relic
            // etc.

        } catch (Exception e) {
            log.error("Error recording metric {}: {}", metricName, e.getMessage(), e);
        }
    }

    /**
     * Record anomaly detection metric
     */
    public void recordAnomalyDetection(String anomalyType, String severity, boolean blocked) {
        recordMetric("security.anomaly.detected", 1.0, Map.of(
            "type", anomalyType,
            "severity", severity,
            "blocked", String.valueOf(blocked)
        ));
    }

    /**
     * Record authentication metric
     */
    public void recordAuthentication(String result, String method, int riskScore) {
        recordMetric("security.authentication", 1.0, Map.of(
            "result", result,
            "method", method,
            "risk_level", getRiskLevel(riskScore)
        ));
    }

    /**
     * Increment counter
     */
    public void incrementCounter(String counterName, Map<String, String> tags) {
        recordMetric(counterName, 1.0, tags);
    }

    /**
     * Get risk level from score
     */
    private String getRiskLevel(int score) {
        if (score >= 80) return "HIGH";
        if (score >= 60) return "MEDIUM";
        if (score >= 30) return "LOW";
        return "MINIMAL";
    }
}
