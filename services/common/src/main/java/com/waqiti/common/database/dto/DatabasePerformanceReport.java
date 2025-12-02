package com.waqiti.common.database.dto;

import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Database performance report containing comprehensive performance metrics.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class DatabasePerformanceReport {
    private Instant timestamp;
    private Instant reportTimestamp;
    private OverallStats overallStats;
    private List<SlowQueryReport> topSlowQueries;
    private List<ConnectionPoolStatus> connectionPoolStatuses;
    private List<IndexRecommendation> indexRecommendations;
    private CachePerformanceStats cachePerformanceStats;
    
    // Original fields
    private QueryPatternAnalysis queryAnalysis;
    private QueryPatternAnalysis queryPatternAnalysis;
    private CacheMetrics cacheMetrics;
    private ConnectionPoolStats poolStats;
    private ConnectionPoolStatus connectionPoolStatus;
    private List<SlowQuery> slowQueries;
    private Map<String, Double> tableScanMetrics;
    private double overallHealthScore;
    private List<String> warnings;
    private List<String> recommendations;
}