package com.waqiti.common.database.dto;

import lombok.Data;

/**
 * Cache metrics for monitoring query cache performance.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class CacheMetrics {
    private long hitCount;
    private long missCount;
    private double hitRate;
    private long evictionCount;
    private long size;
    private long maxSize;
}