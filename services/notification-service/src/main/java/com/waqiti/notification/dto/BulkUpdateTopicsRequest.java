package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import com.waqiti.common.validation.RateLimit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * Request for bulk updating multiple notification topics with comprehensive validation
 * Supports enterprise-grade bulk update operations with conflict resolution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RateLimit(maxRequests = 50, windowInSeconds = 3600, keyExpression = "#request.requestorId")
public class BulkUpdateTopicsRequest {
    
    @NotEmpty(message = "Topic updates cannot be empty")
    @Size(min = 1, max = 500, message = "Topic updates must contain between 1 and 500 items")
    @Valid
    private Map<String, UpdateTopicRequest> topicUpdates; // topicId -> updateRequest
    
    @NotNull(message = "Requestor ID is required for auditing")
    private String requestorId;
    
    @Builder.Default
    private boolean skipNotFound = true;
    
    @Builder.Default
    private boolean skipUnauthorized = false;
    
    @Builder.Default
    private boolean stopOnError = false;
    
    @Builder.Default
    private boolean enableRollback = true;
    
    @Builder.Default
    private ConflictResolution conflictResolution = ConflictResolution.MERGE;
    
    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 50, message = "Batch size cannot exceed 50")
    @Builder.Default
    private int batchSize = 25;
    
    @Builder.Default
    private int maxRetries = 3;
    
    @Builder.Default
    private long retryDelayMillis = 1500L;
    
    @Builder.Default
    private UpdateMode updateMode = UpdateMode.PARTIAL;
    
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    private Set<String> fieldsToUpdate;
    
    private Set<String> fieldsToIgnore;
    
    private String reason;
    
    private String idempotencyKey;
    
    private Map<String, String> metadata;
    
    private LocalDateTime scheduledAt;
    
    @Builder.Default
    private boolean enableAuditTrail = true;
    
    @Builder.Default
    private boolean validatePermissions = true;
    
    @Builder.Default
    private boolean dryRun = false;
    
    public enum ConflictResolution {
        SKIP("Skip updates that would cause conflicts"),
        OVERWRITE("Overwrite existing values with new ones"),
        MERGE("Merge new values with existing ones"),
        FAIL("Fail the entire operation on any conflict"),
        PROMPT("Require manual resolution for conflicts");
        
        private final String description;
        
        ConflictResolution(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum UpdateMode {
        PARTIAL("Update only specified fields"),
        COMPLETE("Replace entire topic configuration"),
        INCREMENTAL("Apply incremental changes"),
        CONDITIONAL("Update only if conditions are met");
        
        private final String description;
        
        UpdateMode(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum Priority {
        LOW(1, "Low priority updates"),
        NORMAL(5, "Normal priority updates"),
        HIGH(10, "High priority updates"),
        URGENT(15, "Urgent updates with immediate processing");
        
        private final int weight;
        private final String description;
        
        Priority(int weight, String description) {
            this.weight = weight;
            this.description = description;
        }
        
        public int getWeight() {
            return weight;
        }
        
        public String getDescription() {
            return description;
        }
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
        if (topicUpdates.isEmpty()) {
            throw new IllegalArgumentException("Topic updates cannot be empty");
        }
        
        if (scheduledAt != null && scheduledAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled time cannot be in the past");
        }
        
        if (batchSize > topicUpdates.size()) {
            batchSize = topicUpdates.size();
        }
        
        // Validate topic IDs are unique
        if (topicUpdates.keySet().size() != topicUpdates.size()) {
            throw new IllegalArgumentException("Duplicate topic IDs found in request");
        }
        
        // Validate fields to update and ignore don't overlap
        if (fieldsToUpdate != null && fieldsToIgnore != null) {
            Set<String> intersection = Set.copyOf(fieldsToUpdate);
            intersection.retainAll(fieldsToIgnore);
            if (!intersection.isEmpty()) {
                throw new IllegalArgumentException("Fields cannot be both updated and ignored: " + intersection);
            }
        }
    }
    
    /**
     * Calculates estimated processing time in milliseconds
     */
    public long getEstimatedProcessingTimeMs() {
        int batches = (int) Math.ceil((double) topicUpdates.size() / batchSize);
        long baseTimePerBatch = 750L; // Base processing time per batch
        long complexityMultiplier = updateMode == UpdateMode.COMPLETE ? 2L : 1L;
        long retryOverhead = maxRetries * retryDelayMillis;
        return batches * (baseTimePerBatch * complexityMultiplier + retryOverhead);
    }
    
    /**
     * Gets the number of batches required for processing
     */
    public int getBatchCount() {
        return (int) Math.ceil((double) topicUpdates.size() / batchSize);
    }
    
    /**
     * Checks if the update is scheduled
     */
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * Gets the topic IDs to be updated
     */
    public Set<String> getTopicIds() {
        return topicUpdates.keySet();
    }
}