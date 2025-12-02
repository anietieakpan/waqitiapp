package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Notification event subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventSubscription {
    
    /**
     * Subscription ID
     */
    private String subscriptionId;
    
    /**
     * Subscriber ID
     */
    private String subscriberId;
    
    /**
     * Event types to subscribe to
     */
    private List<EventType> eventTypes;
    
    /**
     * Webhook URL for callbacks
     */
    private String webhookUrl;
    
    /**
     * Webhook headers
     */
    private Map<String, String> webhookHeaders;
    
    /**
     * Filter criteria
     */
    private EventFilter filter;
    
    /**
     * Subscription status
     */
    private SubscriptionStatus status;
    
    /**
     * Retry configuration
     */
    private RetryConfiguration retryConfig;
    
    /**
     * Created timestamp
     */
    private Instant createdAt;
    
    /**
     * Last event timestamp
     */
    private Instant lastEventAt;
    
    /**
     * Event count
     */
    private long eventCount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventFilter {
        private List<NotificationChannel> channels;
        private List<String> categories;
        private List<String> userIds;
        private Map<String, String> customFilters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfiguration {
        private int maxRetries;
        private long retryDelayMs;
        private double backoffMultiplier;
        private long maxRetryDelayMs;
    }
    
    public enum EventType {
        NOTIFICATION_SENT,
        NOTIFICATION_DELIVERED,
        NOTIFICATION_OPENED,
        NOTIFICATION_CLICKED,
        NOTIFICATION_BOUNCED,
        NOTIFICATION_COMPLAINED,
        NOTIFICATION_FAILED,
        TEMPLATE_CREATED,
        TEMPLATE_UPDATED,
        TEMPLATE_DELETED,
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_UPDATED,
        SUBSCRIPTION_CANCELLED
    }
    
    public enum SubscriptionStatus {
        ACTIVE,
        PAUSED,
        EXPIRED,
        CANCELLED,
        ERROR
    }
}