package com.waqiti.common.integration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;

/**
 * Comprehensive service integration report
 */
@Data
@Builder
@Jacksonized
public class ServiceIntegrationReport {
    private int totalServices;
    private int healthyServices;
    private int unhealthyServices;
    private double overallHealthScore;
    private Map<String, ServiceHealthInfo> serviceHealthDetails;
    private Map<String, Object> performanceMetrics;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant generatedAt;
    
    private String reportVersion;
    private Map<String, Object> systemMetrics;
    
    @Data
    @Builder
    @Jacksonized
    public static class ServiceHealthInfo {
        private String serviceName;
        private String status;
        private double healthScore;
        private long totalCalls;
        private double successRate;
        private double averageResponseTime;
        private Instant lastCheck;
        private boolean circuitBreakerOpen;
    }
}