package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * Base notification request class
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class NotificationRequest {
    
    /**
     * Unique request ID for tracking
     */
    private String requestId;
    
    /**
     * User ID of the recipient
     */
    private String userId;
    
    /**
     * Notification channel
     */
    private NotificationChannel channel;
    
    /**
     * Notification type/category
     */
    private NotificationType type;
    
    /**
     * Priority level
     */
    private Priority priority;
    
    /**
     * Whether this notification requires delivery confirmation
     */
    private boolean requireConfirmation;
    
    /**
     * Retry configuration
     */
    private RetryConfig retryConfig;
    
    /**
     * Expiration time for the notification
     */
    private Instant expiresAt;
    
    /**
     * Metadata for tracking and analytics
     */
    private Map<String, Object> metadata;
    
    /**
     * Correlation ID for linking related notifications
     */
    private String correlationId;
    
    /**
     * Source service that initiated the notification
     */
    private String sourceService;
    
    /**
     * Whether to respect user preferences
     */
    private boolean respectUserPreferences;
    
    /**
     * Rate limiting key
     */
    private String rateLimitKey;
    
    /**
     * Localization settings
     */
    private LocalizationSettings localizationSettings;
    
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfig {
        private int maxRetries;
        private long retryDelayMs;
        private double backoffMultiplier;
        private long maxRetryDelayMs;
    }
    
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalizationSettings {
        private String locale;
        private String timezone;
        private String dateFormat;
        private String currencyCode;
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        MEDIUM,
        HIGH,
        URGENT,
        CRITICAL
    }
}