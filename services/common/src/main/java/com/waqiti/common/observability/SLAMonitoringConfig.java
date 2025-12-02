package com.waqiti.common.observability;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for SLA monitoring and alerting
 * Defines thresholds, monitoring intervals, and alert policies
 */
@Data
@Builder
public class SLAMonitoringConfig {
    
    private boolean enabled;
    private Duration evaluationInterval;
    private Duration retentionPeriod;
    private boolean alertingEnabled;
    private Map<String, SLAThreshold> slaThresholds;
    private List<String> alertChannels;
    private int breachToleranceCount;
    private Duration cooldownPeriod;
    private Duration paymentProcessingSLO;
    private Duration userRegistrationSLO;
    private Duration databaseQuerySLO;
    private Duration apiResponseSLO;
    private double availabilityTarget;
    
    /**
     * Default SLA monitoring configuration
     */
    public static SLAMonitoringConfig defaultConfig() {
        return SLAMonitoringConfig.builder()
            .enabled(true)
            .evaluationInterval(Duration.ofMinutes(1))
            .retentionPeriod(Duration.ofDays(30))
            .alertingEnabled(true)
            .breachToleranceCount(3)
            .cooldownPeriod(Duration.ofMinutes(15))
            .alertChannels(List.of("slack", "email", "webhook"))
            .slaThresholds(Map.of(
                "response_time", new SLAThreshold("response_time", 1000.0, 2000.0, "ms"),
                "error_rate", new SLAThreshold("error_rate", 1.0, 5.0, "%"),
                "availability", new SLAThreshold("availability", 99.9, 99.0, "%"),
                "throughput", new SLAThreshold("throughput", 100.0, 50.0, "rps")
            ))
            .build();
    }
    
    /**
     * Get SLA threshold for a specific metric
     */
    public SLAThreshold getThreshold(String metricName) {
        return slaThresholds != null ? slaThresholds.get(metricName) : null;
    }
    
    /**
     * Check if metric value breaches SLA threshold
     */
    public boolean isBreaching(String metricName, double value) {
        SLAThreshold threshold = getThreshold(metricName);
        return threshold != null && threshold.isBreaching(value);
    }
    
    @Data
    public static class SLAThreshold {
        private final String metricName;
        private final double warningThreshold;
        private final double criticalThreshold;
        private final String unit;
        
        public boolean isBreaching(double value) {
            // For metrics like error_rate, higher is worse
            if ("error_rate".equals(metricName)) {
                return value > criticalThreshold;
            }
            // For metrics like availability, lower is worse  
            if ("availability".equals(metricName)) {
                return value < criticalThreshold;
            }
            // For response_time, higher is worse
            if ("response_time".equals(metricName)) {
                return value > criticalThreshold;
            }
            // For throughput, lower is worse
            if ("throughput".equals(metricName)) {
                return value < criticalThreshold;
            }
            return false;
        }
        
        public boolean isWarning(double value) {
            if ("error_rate".equals(metricName)) {
                return value > warningThreshold && value <= criticalThreshold;
            }
            if ("availability".equals(metricName)) {
                return value < warningThreshold && value >= criticalThreshold;
            }
            if ("response_time".equals(metricName)) {
                return value > warningThreshold && value <= criticalThreshold;
            }
            if ("throughput".equals(metricName)) {
                return value < warningThreshold && value >= criticalThreshold;
            }
            return false;
        }
    }
}