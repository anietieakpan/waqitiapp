package com.waqiti.notification.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event model for in-app notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotificationEvent {
    
    private String notificationId;
    private String userId;
    private String type; // SHOW_NOTIFICATION, BATCH_NOTIFICATION, etc.
    private String title;
    private String message;
    private String icon;
    private String category;
    private String priority; // low, normal, high, urgent
    private String templateId;
    private String batchId;
    private String scheduleId;
    private String actionId;
    private List<String> notificationIds; // For bulk operations
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant expiry;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant scheduledTime;
    
    private List<NotificationAction> actions;
    private Map<String, Object> templateVariables;
    private Map<String, Object> actionData;
    private Map<String, Object> metadata;
    
    private NotificationSettings notificationSettings;
    private boolean sticky; // Whether notification should remain until dismissed
    
    // Validation methods
    public boolean isValid() {
        return notificationId != null && !notificationId.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty() &&
               type != null && !type.trim().isEmpty();
    }
    
    public boolean isExpired() {
        return expiry != null && Instant.now().isAfter(expiry);
    }
    
    public boolean isScheduled() {
        return scheduledTime != null && Instant.now().isBefore(scheduledTime);
    }
    
    public boolean isHighPriority() {
        return "high".equalsIgnoreCase(priority) || "urgent".equalsIgnoreCase(priority);
    }
    
    public int getPriorityLevel() {
        switch (priority != null ? priority.toLowerCase() : "normal") {
            case "urgent": return 1;
            case "high": return 2;
            case "normal": return 3;
            case "low": return 4;
            default: return 3;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationAction {
        private String id;
        private String label;
        private String type; // BUTTON, LINK, DISMISS
        private String url;
        private String style; // primary, secondary, danger
        private Map<String, Object> data;
        private boolean dismissOnClick;
    }
}