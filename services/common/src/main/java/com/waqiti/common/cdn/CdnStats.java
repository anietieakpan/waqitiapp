package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * CDN statistics and metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdnStats {
    
    /**
     * Distribution ID these stats are for
     */
    private String distributionId;
    
    /**
     * Total bandwidth used (bytes)
     */
    private long totalBandwidth;
    
    /**
     * Total number of requests
     */
    private long totalRequests;
    
    /**
     * Cache hit ratio (0-1)
     */
    private double cacheHitRatio;
    
    /**
     * Average response time (ms)
     */
    private double averageResponseTime;
    
    /**
     * Total unique visitors
     */
    private long uniqueVisitors;
    
    /**
     * Bandwidth by region
     */
    private Map<String, Long> bandwidthByRegion;
    
    /**
     * Requests by region
     */
    private Map<String, Long> requestsByRegion;
    
    /**
     * Top requested objects
     */
    private Map<String, ObjectStats> topObjects;
    
    /**
     * Error rate (4xx and 5xx)
     */
    private double errorRate;
    
    /**
     * Status code distribution
     */
    private Map<Integer, Long> statusCodeDistribution;
    
    /**
     * Origin bandwidth (bytes)
     */
    private long originBandwidth;
    
    /**
     * Edge bandwidth (bytes)
     */
    private long edgeBandwidth;
    
    /**
     * Cost estimate for the period
     */
    private double estimatedCost;
    
    /**
     * Time period for these stats
     */
    private TimePeriod timePeriod;
    
    /**
     * Edge location performance
     */
    private Map<String, EdgeLocationMetrics> edgeLocationMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObjectStats {
        private String objectKey;
        private long requests;
        private long bandwidth;
        private double cacheHitRatio;
        private double averageResponseTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePeriod {
        private Instant startTime;
        private Instant endTime;
        private String timezone;
    }
}