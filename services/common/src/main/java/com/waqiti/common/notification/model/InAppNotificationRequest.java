package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * In-app notification request
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InAppNotificationRequest extends NotificationRequest {
    
    /**
     * Notification title
     */
    private String title;
    
    /**
     * Notification message
     */
    private String message;
    
    /**
     * Rich content (HTML)
     */
    private String richContent;
    
    /**
     * Icon or avatar URL
     */
    private String iconUrl;
    
    /**
     * Action URL or deep link
     */
    private String actionUrl;
    
    /**
     * Action button text
     */
    private String actionText;
    
    /**
     * Secondary action
     */
    private Map<String, Object> secondaryAction;
    
    /**
     * Category for grouping
     */
    private String category;
    
    /**
     * Whether notification is persistent
     */
    private boolean persistent;
    
    /**
     * Auto-dismiss timeout in seconds
     */
    private Integer autoDismissSeconds;
    
    /**
     * Display position
     */
    @Builder.Default
    private DisplayPosition position = DisplayPosition.TOP_RIGHT;
    
    /**
     * Visual style
     */
    @Builder.Default
    private NotificationStyle style = NotificationStyle.DEFAULT;
    
    /**
     * Additional data
     */
    private Map<String, Object> data;
    
    /**
     * Whether to show in notification center
     */
    @Builder.Default
    private boolean showInNotificationCenter = true;
    
    /**
     * Whether to play sound
     */
    @Builder.Default
    private boolean playSound = true;
    
    /**
     * Whether to vibrate (mobile)
     */
    @Builder.Default
    private boolean vibrate = true;
    
    /**
     * Expiry time
     */
    private Instant expiresAt;
    
    /**
     * Target segments for the notification
     */
    private Map<String, Object> targetSegments;
    
    
    public enum DisplayPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
        CENTER
    }
    
    public enum NotificationStyle {
        DEFAULT,
        SUCCESS,
        WARNING,
        ERROR,
        INFO,
        CUSTOM
    }
    
    public enum ActionType {
        URL,
        DEEP_LINK,
        DISMISS,
        CUSTOM
    }
}