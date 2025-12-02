package com.waqiti.common.messaging.model;

/**
 * Enumeration of backoff types for retry policies
 */
public enum BackoffType {
    /**
     * Fixed delay between retries
     */
    FIXED,
    
    /**
     * Exponential backoff with increasing delays
     */
    EXPONENTIAL,
    
    /**
     * Linear backoff with linearly increasing delays
     */
    LINEAR,
    
    /**
     * Random jitter backoff
     */
    RANDOM_JITTER
}