package com.waqiti.common.metrics;

/**
 * Cache operation types for metrics tracking
 */
public enum CacheOperation {
    GET,
    PUT,
    DELETE,
    EVICT,
    CLEAR,
    WARM_UP
}