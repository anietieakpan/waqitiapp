package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RetryConfig {
    private int maxRetries;
    private int currentAttempt;
    private long baseDelayMs;
    private long maxDelayMs;
    private double jitterFactor;
    private List<String> retryableErrors;
    
    public boolean isRetryableError(String error) {
        if (retryableErrors == null || retryableErrors.isEmpty()) {
            return true;
        }
        return retryableErrors.stream().anyMatch(error::contains);
    }
}