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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import com.waqiti.common.validation.RateLimit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Request for sending notifications to multiple users in bulk with comprehensive validation
 * Supports high-volume notifications with advanced targeting and delivery tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RateLimit(maxRequests = 50, windowInSeconds = 3600, keyExpression = "#request.senderId")
public class BulkNotificationRequest {
    
    @NotEmpty(message = "User IDs list cannot be empty")
    @Size(min = 1, max = 10000, message = "User IDs list must contain between 1 and 10,000 items")
    private List<@Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Invalid user ID format") String> userIds;
    
    @NotNull(message = "Notification details are required")
    @Valid
    private PushSendNotificationRequest notification;
    
    @NotNull(message = "Sender ID is required for tracking")
    private String senderId;
    
    @Builder.Default
    private boolean sendInBatches = true;
    
    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 1000, message = "Batch size cannot exceed 1000")
    @Builder.Default
    private int batchSize = 500;
    
    @Builder.Default
    private boolean skipInactiveDevices = true;
    
    @Builder.Default
    private boolean skipUnsubscribedUsers = true;
    
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    @Builder.Default
    private DeliveryMode deliveryMode = DeliveryMode.BEST_EFFORT;
    
    private Set<String> deviceTypes; // ios, android, web
    
    private Set<String> preferredLanguages;
    
    private Map<String, String> segmentationCriteria;
    
    @Builder.Default
    private int maxRetries = 3;
    
    @Builder.Default
    private long retryDelayMillis = 2000L;
    
    private LocalDateTime scheduledAt;
    
    private LocalDateTime expiresAt;
    
    @Builder.Default
    private boolean enableDeliveryTracking = true;
    
    @Builder.Default
    private boolean enableAnalytics = true;
    
    private String campaignId;
    
    private String idempotencyKey;
    
    private Map<String, String> metadata;
    
    @Builder.Default
    private boolean dryRun = false;
    
    public enum Priority {
        LOW(1, "Low priority delivery"),
        NORMAL(5, "Normal priority delivery"),
        HIGH(10, "High priority delivery"),
        URGENT(15, "Urgent delivery with immediate processing"),
        CRITICAL(20, "Critical system notifications");
        
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
    
    public enum DeliveryMode {
        BEST_EFFORT("Deliver to as many devices as possible, ignore failures"),
        GUARANTEED("Ensure delivery to all devices, retry on failures"),
        ATOMIC("All or nothing delivery");
        
        private final String description;
        
        DeliveryMode(String description) {
            this.description = description;
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
        if (userIds.isEmpty()) {
            throw new IllegalArgumentException("User IDs list cannot be empty");
        }
        
        if (scheduledAt != null && scheduledAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled time cannot be in the past");
        }
        
        if (expiresAt != null && scheduledAt != null && expiresAt.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("Expiration time cannot be before scheduled time");
        }
        
        if (batchSize > userIds.size()) {
            batchSize = userIds.size();
        }
        
        // Validate user IDs are unique
        long uniqueUserIds = userIds.stream().distinct().count();
        if (uniqueUserIds != userIds.size()) {
            throw new IllegalArgumentException("Duplicate user IDs found in request");
        }
    }
    
    /**
     * Calculates estimated delivery time in milliseconds
     */
    public long getEstimatedDeliveryTimeMs() {
        int batches = (int) Math.ceil((double) userIds.size() / batchSize);
        long baseTimePerBatch = 1000L; // Base delivery time per batch
        long priorityMultiplier = Math.max(1, 6 - priority.getWeight() / 4); // Higher priority = faster
        long retryOverhead = maxRetries * retryDelayMillis;
        return batches * (baseTimePerBatch * priorityMultiplier + retryOverhead);
    }
    
    /**
     * Gets the number of batches required for processing
     */
    public int getBatchCount() {
        return (int) Math.ceil((double) userIds.size() / batchSize);
    }
    
    /**
     * Checks if the notification is scheduled
     */
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * Checks if the notification has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}