package com.waqiti.common.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumer Health monitoring data structure
 * Tracks the operational health and performance of Kafka consumers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsumerHealth {

    @JsonProperty("consumerName")
    private String consumerName;

    @JsonProperty("instanceId")
    private String instanceId;

    @JsonProperty("status")
    @Builder.Default
    private HealthStatus status = HealthStatus.HEALTHY;

    @JsonProperty("lastHeartbeat")
    @Builder.Default
    private Instant lastHeartbeat = Instant.now();

    @JsonProperty("lagMilliseconds")
    @Builder.Default
    private Long lagMilliseconds = 0L;

    @JsonProperty("processingRate")
    @Builder.Default
    private Double processingRate = 0.0; // events per second

    @JsonProperty("errorRate")
    @Builder.Default
    private Double errorRate = 0.0; // percentage

    @JsonProperty("memoryUsageMb")
    private Integer memoryUsageMb;

    @JsonProperty("cpuUsagePercent")
    private Double cpuUsagePercent;

    @JsonProperty("activeThreads")
    private Integer activeThreads;

    @JsonProperty("details")
    private Map<String, Object> details;

    @JsonProperty("alertThresholdBreached")
    @Builder.Default
    private Boolean alertThresholdBreached = false;

    @JsonProperty("metrics")
    private ConsumerMetrics metrics;

    /**
     * Health status enumeration
     */
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        STOPPED,
        UNKNOWN
    }

    /**
     * Consumer performance metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConsumerMetrics {
        private Long totalMessagesProcessed;
        private Long totalMessagesFailed;
        private Double averageProcessingTimeMs;
        private Long minProcessingTimeMs;
        private Long maxProcessingTimeMs;
        private Instant lastMessageProcessedAt;
        private Long messagesInQueue;
        private Integer partitionsAssigned;
        private Map<String, Long> topicMessageCounts;
    }

    /**
     * Add detail information
     */
    public void addDetail(String key, Object value) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.put(key, value);
    }

    /**
     * Get detail value
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key, Class<T> type) {
        if (details == null || !details.containsKey(key)) {
            return null;
        }
        
        Object value = details.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }

    /**
     * Check if consumer is healthy
     */
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY;
    }

    /**
     * Check if consumer is operational (healthy or degraded)
     */
    public boolean isOperational() {
        return status == HealthStatus.HEALTHY || status == HealthStatus.DEGRADED;
    }

    /**
     * Check if heartbeat is recent
     */
    public boolean hasRecentHeartbeat(long thresholdMs) {
        if (lastHeartbeat == null) {
            return false;
        }
        
        long ageMs = Instant.now().toEpochMilli() - lastHeartbeat.toEpochMilli();
        return ageMs <= thresholdMs;
    }

    /**
     * Update heartbeat timestamp
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    /**
     * Check if lag is within acceptable limits
     */
    public boolean isLagAcceptable(long maxAcceptableLagMs) {
        return lagMilliseconds != null && lagMilliseconds <= maxAcceptableLagMs;
    }

    /**
     * Check if processing rate is acceptable
     */
    public boolean isProcessingRateAcceptable(double minAcceptableRate) {
        return processingRate != null && processingRate >= minAcceptableRate;
    }

    /**
     * Check if error rate is acceptable
     */
    public boolean isErrorRateAcceptable(double maxAcceptableErrorRate) {
        return errorRate != null && errorRate <= maxAcceptableErrorRate;
    }

    /**
     * Evaluate overall health based on thresholds
     */
    public HealthStatus evaluateHealth(HealthThresholds thresholds) {
        if (!hasRecentHeartbeat(thresholds.getHeartbeatTimeoutMs())) {
            return HealthStatus.STOPPED;
        }

        int unhealthyIndicators = 0;
        int degradedIndicators = 0;

        // Check lag
        if (lagMilliseconds != null) {
            if (lagMilliseconds > thresholds.getCriticalLagMs()) {
                unhealthyIndicators++;
            } else if (lagMilliseconds > thresholds.getWarningLagMs()) {
                degradedIndicators++;
            }
        }

        // Check error rate
        if (errorRate != null) {
            if (errorRate > thresholds.getCriticalErrorRate()) {
                unhealthyIndicators++;
            } else if (errorRate > thresholds.getWarningErrorRate()) {
                degradedIndicators++;
            }
        }

        // Check processing rate
        if (processingRate != null && thresholds.getMinProcessingRate() > 0) {
            if (processingRate < thresholds.getMinProcessingRate() * 0.5) {
                unhealthyIndicators++;
            } else if (processingRate < thresholds.getMinProcessingRate()) {
                degradedIndicators++;
            }
        }

        // Check memory usage
        if (memoryUsageMb != null && thresholds.getMaxMemoryUsageMb() > 0) {
            if (memoryUsageMb > thresholds.getMaxMemoryUsageMb()) {
                unhealthyIndicators++;
            } else if (memoryUsageMb > thresholds.getMaxMemoryUsageMb() * 0.8) {
                degradedIndicators++;
            }
        }

        // Check CPU usage
        if (cpuUsagePercent != null) {
            if (cpuUsagePercent > thresholds.getMaxCpuUsagePercent()) {
                unhealthyIndicators++;
            } else if (cpuUsagePercent > thresholds.getMaxCpuUsagePercent() * 0.8) {
                degradedIndicators++;
            }
        }

        // Determine status
        if (unhealthyIndicators > 0) {
            this.alertThresholdBreached = true;
            return HealthStatus.UNHEALTHY;
        } else if (degradedIndicators > 0) {
            this.alertThresholdBreached = true;
            return HealthStatus.DEGRADED;
        } else {
            this.alertThresholdBreached = false;
            return HealthStatus.HEALTHY;
        }
    }

    /**
     * Health threshold configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthThresholds {
        @Builder.Default
        private Long heartbeatTimeoutMs = 60000L; // 1 minute
        
        @Builder.Default
        private Long warningLagMs = 30000L; // 30 seconds
        
        @Builder.Default
        private Long criticalLagMs = 300000L; // 5 minutes
        
        @Builder.Default
        private Double warningErrorRate = 1.0; // 1%
        
        @Builder.Default
        private Double criticalErrorRate = 5.0; // 5%
        
        @Builder.Default
        private Double minProcessingRate = 0.0; // events per second
        
        @Builder.Default
        private Integer maxMemoryUsageMb = 1024; // 1GB
        
        @Builder.Default
        private Double maxCpuUsagePercent = 80.0; // 80%
    }

    /**
     * Get age of last heartbeat in milliseconds
     */
    public long getHeartbeatAgeMs() {
        if (lastHeartbeat == null) {
            return Long.MAX_VALUE;
        }
        return Instant.now().toEpochMilli() - lastHeartbeat.toEpochMilli();
    }

    /**
     * Create health summary for logging
     */
    public String getHealthSummary() {
        return String.format("Consumer Health [%s/%s: %s, Lag: %dms, Rate: %.2f/s, Error: %.2f%%, CPU: %.1f%%, Mem: %dMB]",
                           consumerName, instanceId, status, 
                           lagMilliseconds != null ? lagMilliseconds : 0,
                           processingRate != null ? processingRate : 0.0,
                           errorRate != null ? errorRate : 0.0,
                           cpuUsagePercent != null ? cpuUsagePercent : 0.0,
                           memoryUsageMb != null ? memoryUsageMb : 0);
    }

    /**
     * Check if any critical thresholds are breached
     */
    public boolean hasCriticalIssues(HealthThresholds thresholds) {
        return !hasRecentHeartbeat(thresholds.getHeartbeatTimeoutMs()) ||
               (lagMilliseconds != null && lagMilliseconds > thresholds.getCriticalLagMs()) ||
               (errorRate != null && errorRate > thresholds.getCriticalErrorRate()) ||
               (memoryUsageMb != null && thresholds.getMaxMemoryUsageMb() > 0 &&
                memoryUsageMb > thresholds.getMaxMemoryUsageMb()) ||
               (cpuUsagePercent != null && cpuUsagePercent > thresholds.getMaxCpuUsagePercent());
    }

    /**
     * Check if alert threshold is breached
     */
    public boolean isAlertThresholdBreached() {
        return alertThresholdBreached != null && alertThresholdBreached;
    }

    @Override
    public String toString() {
        return getHealthSummary();
    }
}