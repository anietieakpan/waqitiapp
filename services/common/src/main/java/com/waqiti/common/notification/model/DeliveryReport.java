package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive delivery report for notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryReport {
    
    /**
     * Report ID
     */
    private String reportId;
    
    /**
     * Notification ID
     */
    private String notificationId;
    
    /**
     * Batch ID if part of batch
     */
    private String batchId;
    
    /**
     * Channel used
     */
    private NotificationChannel channel;
    
    /**
     * Final delivery status
     */
    private DeliveryStatus deliveryStatus;
    
    /**
     * Recipient information
     */
    private RecipientInfo recipientInfo;
    
    /**
     * Provider details
     */
    private ProviderDetails providerDetails;
    
    /**
     * Delivery timeline
     */
    private DeliveryTimeline timeline;
    
    /**
     * Engagement metrics
     */
    private EngagementMetrics engagement;
    
    /**
     * Error information if failed
     */
    private List<DeliveryError> errors;
    
    /**
     * Cost information
     */
    private CostInfo costInfo;
    
    /**
     * Report metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientInfo {
        private String userId;
        private String email;
        private String phoneNumber;
        private String deviceToken;
        private String recipientType;
        private Map<String, String> attributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderDetails {
        private String providerName;
        private String providerMessageId;
        private String providerStatus;
        private Map<String, String> providerMetadata;
        private String providerRegion;
        private String providerEndpoint;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryTimeline {
        private Instant createdAt;
        private Instant queuedAt;
        private Instant sentAt;
        private Instant deliveredAt;
        private Instant openedAt;
        private Instant clickedAt;
        private Instant bouncedAt;
        private Instant complainedAt;
        private List<TimelineEvent> events;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private Instant timestamp;
        private String eventType;
        private String description;
        private Map<String, String> details;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementMetrics {
        private boolean delivered;
        private boolean opened;
        private int openCount;
        private boolean clicked;
        private int clickCount;
        private List<ClickInfo> clicks;
        private boolean unsubscribed;
        private boolean forwarded;
        private boolean replied;
        private Map<String, Object> customMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClickInfo {
        private String url;
        private Instant clickedAt;
        private String userAgent;
        private String ipAddress;
        private Map<String, String> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryError {
        private Instant timestamp;
        private String errorCode;
        private String errorMessage;
        private String errorType;
        private boolean permanent;
        private String diagnosticCode;
        private Map<String, String> additionalInfo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostInfo {
        private double baseCost;
        private double additionalCost;
        private double totalCost;
        private String currency;
        private String billingCategory;
        private Map<String, Double> costBreakdown;
    }
    
    public enum DeliveryStatus {
        DELIVERED,
        PENDING,
        BOUNCED,
        COMPLAINED,
        FAILED,
        DEFERRED,
        EXPIRED,
        SUPPRESSED
    }
}