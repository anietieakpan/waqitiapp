package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive notification status tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatus {
    
    /**
     * Notification ID
     */
    private String notificationId;
    
    /**
     * Current status
     */
    private Status status;
    
    /**
     * Channel being used
     */
    private NotificationChannel channel;
    
    /**
     * Delivery attempts
     */
    private int deliveryAttempts;
    
    /**
     * Time of last attempt
     */
    private Instant lastAttemptTime;
    
    /**
     * Time of successful delivery
     */
    private Instant deliveredAt;
    
    /**
     * Time status was last updated
     */
    private Instant updatedAt;
    
    /**
     * Provider used for delivery
     */
    private String provider;
    
    /**
     * Provider-specific message ID
     */
    private String providerMessageId;
    
    /**
     * Provider response
     */
    private String providerResponse;
    
    /**
     * Error details if failed
     */
    private ErrorDetails errorDetails;
    
    /**
     * Delivery metrics
     */
    private DeliveryMetrics deliveryMetrics;
    
    /**
     * Status history
     */
    private List<StatusHistory> statusHistory;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails {
        private String errorCode;
        private String errorMessage;
        private String errorType;
        private boolean retryable;
        private Instant nextRetryTime;
        private Map<String, String> additionalInfo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryMetrics {
        private long queueTimeMs;
        private long processingTimeMs;
        private long totalTimeMs;
        private boolean opened;
        private Instant openedAt;
        private boolean clicked;
        private Instant clickedAt;
        private boolean bounced;
        private String bounceType;
        private boolean complained;
        private String complaintType;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistory {
        private Status status;
        private Instant timestamp;
        private String reason;
        private Map<String, String> details;
    }
    
    public enum Status {
        PENDING,
        QUEUED,
        PROCESSING,
        SENT,
        DELIVERED,
        OPENED,
        CLICKED,
        BOUNCED,
        COMPLAINED,
        REJECTED,
        FAILED,
        EXPIRED,
        CANCELLED
    }
}