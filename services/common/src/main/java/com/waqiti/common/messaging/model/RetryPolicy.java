package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retry policy configuration for DLQ message recovery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy {
    
    private String topicPattern;
    private int maxRetries;
    private BackoffType backoffType;
    private long fixedDelayMs;
    private long initialDelayMs;
    private double backoffMultiplier;
    private long maxDelayMs;
    private boolean jitterEnabled;
    
    // Circuit breaker settings
    private int failureThreshold;
    private long circuitBreakerTimeoutMs;
    
    // Quarantine settings
    private int poisonMessageThreshold;
    private boolean autoQuarantine;
    
    @Builder.Default
    private boolean enabled = true;
}