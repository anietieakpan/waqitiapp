package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metrics for a specific CDN edge location
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeLocationMetrics {
    
    /**
     * Edge location identifier
     */
    private String locationId;
    
    /**
     * Location name (e.g., "US-East-1", "EU-West-1")
     */
    private String locationName;
    
    /**
     * Geographic coordinates
     */
    private GeoLocation geoLocation;
    
    /**
     * Number of requests served
     */
    private long requestCount;
    
    /**
     * Bandwidth served (bytes)
     */
    private long bandwidth;
    
    /**
     * Cache hit ratio for this location
     */
    private double cacheHitRatio;
    
    /**
     * Average latency (ms)
     */
    private double averageLatency;
    
    /**
     * 95th percentile latency (ms)
     */
    private double p95Latency;
    
    /**
     * 99th percentile latency (ms)
     */
    private double p99Latency;
    
    /**
     * Error rate at this location
     */
    private double errorRate;
    
    /**
     * Current health status
     */
    private HealthStatus healthStatus;
    
    /**
     * Capacity utilization (0-1)
     */
    private double capacityUtilization;
    
    /**
     * Active connections
     */
    private int activeConnections;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private double latitude;
        private double longitude;
        private String city;
        private String country;
        private String region;
    }
    
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
}