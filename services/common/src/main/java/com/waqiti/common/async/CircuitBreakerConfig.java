package com.waqiti.common.async;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for circuit breaker
 */
@Data
@Builder
public class CircuitBreakerConfig {
    
    @Builder.Default
    private int failureThreshold = 5;
    
    @Builder.Default
    private int successThreshold = 3;
    
    @Builder.Default
    private Duration timeout = Duration.ofMinutes(1);
    
    @Builder.Default
    private Duration waitDuration = Duration.ofSeconds(30);
    
    public static CircuitBreakerConfig defaultConfig() {
        return CircuitBreakerConfig.builder().build();
    }
}