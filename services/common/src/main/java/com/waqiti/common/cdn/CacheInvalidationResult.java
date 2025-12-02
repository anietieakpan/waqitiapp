package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Result of a CDN cache invalidation request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationResult {
    
    public static class CacheInvalidationResultBuilder {
        public CacheInvalidationResultBuilder success(boolean success) {
            this.status = success ? InvalidationStatus.COMPLETED : InvalidationStatus.FAILED;
            return this;
        }
        
        public CacheInvalidationResultBuilder error(String error) {
            this.errorMessage = error;
            this.status = InvalidationStatus.FAILED;
            return this;
        }
    }
    
    /**
     * Unique invalidation ID
     */
    private String invalidationId;
    
    /**
     * Status of the invalidation
     */
    private InvalidationStatus status;
    
    /**
     * Paths that were invalidated
     */
    private List<String> paths;
    
    /**
     * Time when invalidation was requested
     */
    private Instant requestTime;
    
    /**
     * Time when invalidation completed
     */
    private Instant completionTime;
    
    /**
     * CloudFront distribution ID
     */
    private String distributionId;
    
    /**
     * Number of objects invalidated
     */
    private int objectCount;
    
    /**
     * Whether this was a wildcard invalidation
     */
    private boolean wildcardInvalidation;
    
    /**
     * Error message if invalidation failed
     */
    private String errorMessage;
    
    /**
     * Cost of the invalidation (if applicable)
     */
    private Double cost;
    
    /**
     * Caller reference
     */
    private String callerReference;
    
    public enum InvalidationStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}