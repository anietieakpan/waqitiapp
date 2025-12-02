package com.waqiti.common.metrics.abstraction;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Cardinality Controller Statistics
 */
@Data
@Builder
public class CardinalityStats {
    private final long totalCardinality;
    private final int metricCount;
    private final long violationCount;
    private final double adaptiveFactor;
    private final long cacheSize;
    private final CacheStats cacheStats;
    private final Map<String, Long> topMetrics;
}