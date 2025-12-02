package com.waqiti.chaos.core;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class ChaosResult {
    
    private String experimentName;
    private boolean success;
    private String message;
    private Long startTime;
    private Long endTime;
    private Map<String, Object> metrics;
    private Throwable error;
    
    public Duration getDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(
                Instant.ofEpochMilli(startTime),
                Instant.ofEpochMilli(endTime)
            );
        }
        return Duration.ZERO;
    }
    
    public static class Builder {
        private Map<String, Object> metrics = new HashMap<>();
        
        public Builder addMetric(String key, Object value) {
            this.metrics.put(key, value);
            return this;
        }
        
        public Builder metrics(Map<String, Object> metrics) {
            this.metrics.putAll(metrics);
            return this;
        }
    }
}