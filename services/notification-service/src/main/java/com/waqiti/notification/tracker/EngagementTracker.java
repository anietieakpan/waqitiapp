package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tracks user engagement with notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngagementTracker {
    
    private String engagementId;
    private String notificationId;
    private String userId;
    private String recipientEmail;
    
    // Engagement metrics
    private boolean opened;
    private int openCount;
    private LocalDateTime firstOpenedAt;
    private LocalDateTime lastOpenedAt;
    private List<OpenEvent> openEvents;
    
    private boolean clicked;
    private int clickCount;
    private LocalDateTime firstClickedAt;
    private LocalDateTime lastClickedAt;
    private List<ClickEvent> clickEvents;
    
    // Device and client information
    private String userAgent;
    private String deviceType; // desktop, mobile, tablet
    private String operatingSystem;
    private String browser;
    private String ipAddress;
    private String location;
    
    // Engagement score
    private double engagementScore;
    private String engagementLevel; // high, medium, low
    
    // Conversion tracking
    private boolean converted;
    private String conversionAction;
    private LocalDateTime convertedAt;
    private Map<String, Object> conversionData;
    
    // Unsubscribe tracking
    private boolean unsubscribed;
    private LocalDateTime unsubscribedAt;
    private String unsubscribeReason;
    
    // Metadata
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenEvent {
        private LocalDateTime openedAt;
        private String userAgent;
        private String ipAddress;
        private String location;
        private Map<String, Object> details;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClickEvent {
        private LocalDateTime clickedAt;
        private String url;
        private String linkId;
        private String userAgent;
        private String ipAddress;
        private String location;
        private Map<String, Object> details;
    }
}