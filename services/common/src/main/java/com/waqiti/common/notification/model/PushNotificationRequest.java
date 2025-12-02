package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Push notification request
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PushNotificationRequest extends NotificationRequest {
    
    /**
     * Target device tokens
     */
    private List<String> deviceTokens;
    
    /**
     * Target topic for topic-based messaging
     */
    private String topic;
    
    /**
     * Notification title
     */
    private String title;
    
    /**
     * Notification body
     */
    private String body;
    
    /**
     * Subtitle (iOS specific)
     */
    private String subtitle;
    
    /**
     * Icon URL or resource
     */
    private String icon;
    
    /**
     * Image URL for rich notifications
     */
    private String imageUrl;
    
    /**
     * Sound to play
     */
    private String sound;
    
    /**
     * Badge count
     */
    private Integer badge;
    
    /**
     * Click action
     */
    private String clickAction;
    
    /**
     * Deep link URL
     */
    private String deepLink;
    
    /**
     * Custom data payload
     */
    private Map<String, Object> data;
    
    /**
     * Platform-specific options as key-value pairs
     */
    private Map<String, Object> platformOptions;
    
    /**
     * Notification category for actionable notifications
     */
    private String category;
    
    /**
     * Thread ID for grouping
     */
    private String threadId;
    
    /**
     * Content availability flag
     */
    private boolean contentAvailable;
    
    /**
     * Mutable content flag
     */
    private boolean mutableContent;
    
    /**
     * Time to live in seconds
     */
    @Builder.Default
    private int ttlSeconds = 2419200; // 28 days
    
    /**
     * Collapse key for replacing notifications
     */
    private String collapseKey;
    
    /**
     * Analytics label
     */
    private String analyticsLabel;
    
    // Platform-specific options flattened to generic map structure
}