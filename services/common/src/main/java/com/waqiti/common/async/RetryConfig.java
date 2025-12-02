package com.waqiti.common.async;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for retry mechanism
 */
@Data
@Builder
public class RetryConfig {
    
    @Builder.Default
    private int maxAttempts = 3;
    
    @Builder.Default
    private Duration initialDelay = Duration.ofSeconds(1);
    
    @Builder.Default
    private Duration maxDelay = Duration.ofSeconds(30);
    
    @Builder.Default
    private boolean jitterEnabled = true;
    
    @Builder.Default
    private double backoffMultiplier = 2.0;
    
    public static RetryConfig defaultConfig() {
        return RetryConfig.builder().build();
    }
    
    public static RetryConfig aggressive() {
        return RetryConfig.builder()
            .maxAttempts(5)
            .initialDelay(Duration.ofMillis(500))
            .maxDelay(Duration.ofSeconds(10))
            .build();
    }
    
    public static RetryConfig conservative() {
        return RetryConfig.builder()
            .maxAttempts(2)
            .initialDelay(Duration.ofSeconds(2))
            .maxDelay(Duration.ofMinutes(1))
            .build();
    }
}