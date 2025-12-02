package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.waqiti.common.validation.RateLimit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * Request to create multiple notification topics in bulk with comprehensive validation
 * Supports enterprise-grade bulk operations with rate limiting and error handling
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RateLimit(maxRequests = 100, windowInSeconds = 3600, keyExpression = "#request.requestorId")
public class BulkCreateTopicsRequest {
    
    @NotEmpty(message = "Topics list cannot be empty")
    @Size(min = 1, max = 1000, message = "Topics list must contain between 1 and 1000 items")
    @Valid
    private List<CreateTopicRequest> topics;
    
    @NotNull(message = "Requestor ID is required for auditing")
    private String requestorId;
    
    @Builder.Default
    private boolean skipExisting = true;
    
    @Builder.Default
    private boolean stopOnError = false;
    
    @Builder.Default
    private boolean enableRollback = true;
    
    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 100, message = "Batch size cannot exceed 100")
    @Builder.Default
    private int batchSize = 50;
    
    @Builder.Default
    private int maxRetries = 3;
    
    @Builder.Default
    private long retryDelayMillis = 1000L;
    
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    @Builder.Default
    private ConflictResolution conflictResolution = ConflictResolution.SKIP;
    
    @Builder.Default
    private boolean enableProgressTracking = true;
    
    private String idempotencyKey;
    
    private Map<String, String> metadata;
    
    private LocalDateTime scheduledAt;
    
    private String correlationId;
    
    public enum Priority {
        LOW(1),
        NORMAL(5),
        HIGH(10),
        CRITICAL(20);
        
        private final int weight;
        
        Priority(int weight) {
            this.weight = weight;
        }
        
        public int getWeight() {
            return weight;
        }
    }
    
    public enum ConflictResolution {
        SKIP,
        OVERWRITE,
        MERGE,
        FAIL
    }
    
    /**
     * Generates a unique idempotency key if not provided
     */
    public String getOrGenerateIdempotencyKey() {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        return idempotencyKey;
    }
    
    /**
     * Validates the request for business rules
     */
    public void validate() {
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("Topics list cannot be empty");
        }
        
        if (batchSize > topics.size()) {
            batchSize = topics.size();
        }
        
        if (scheduledAt != null && scheduledAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled time cannot be in the past");
        }
        
        // Validate topic names for duplicates
        long uniqueTopicNames = topics.stream()
            .map(CreateTopicRequest::getName)
            .distinct()
            .count();
        
        if (uniqueTopicNames != topics.size()) {
            throw new IllegalArgumentException("Duplicate topic names found in request");
        }
    }
    
    /**
     * Calculates estimated processing time in milliseconds
     */
    public long getEstimatedProcessingTimeMs() {
        int batches = (int) Math.ceil((double) topics.size() / batchSize);
        long baseTimePerBatch = 500L; // Base processing time per batch
        long retryOverhead = maxRetries * retryDelayMillis;
        return batches * (baseTimePerBatch + retryOverhead);
    }
}