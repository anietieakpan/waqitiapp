package com.waqiti.common.batch;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for batch processing operations
 */
@Data
@Builder
public class BatchConfiguration {
    
    @Builder.Default
    private int batchSize = 1000;
    
    @Builder.Default
    private int maxConcurrency = 10;
    
    @Builder.Default
    private Duration timeout = Duration.ofMinutes(30);
    
    @Builder.Default
    private int transactionChunkSize = 100;
    
    @Builder.Default
    private boolean continueOnError = true;
    
    @Builder.Default
    private int maxRetries = 3;
    
    @Builder.Default
    private Duration retryDelay = Duration.ofSeconds(1);
    
    public static BatchConfiguration defaultConfig() {
        return BatchConfiguration.builder().build();
    }
    
    public static BatchConfiguration smallBatch() {
        return BatchConfiguration.builder()
            .batchSize(100)
            .maxConcurrency(5)
            .timeout(Duration.ofMinutes(5))
            .build();
    }
    
    public static BatchConfiguration largeBatch() {
        return BatchConfiguration.builder()
            .batchSize(5000)
            .maxConcurrency(20)
            .timeout(Duration.ofHours(2))
            .build();
    }
    
    // Convenience methods for compatibility
    public int getConcurrencyLevel() {
        return maxConcurrency;
    }
    
    public long getTimeoutMs() {
        return timeout.toMillis();
    }
}