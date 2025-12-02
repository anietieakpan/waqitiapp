package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationRequest {

    private List<String> deviceTokens;
    
    private String title;
    
    private String body;
    
    private String icon;
    
    private String sound;
    
    private String badge;
    
    private String tag;
    
    private String color;
    
    private String clickAction;
    
    private Map<String, String> data;
    
    private NotificationPriority priority;
    
    private String collapseKey;
    
    private int timeToLive; // in seconds
    
    @Builder.Default
    private LocalDateTime scheduledFor = LocalDateTime.now();
    
    private String topic;
    
    private String condition;
    
    private boolean mutableContent;
    
    private boolean contentAvailable;
    
    private String categoryId;
    
    private String threadId;

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH
    }

    public boolean hasDeviceTokens() {
        return deviceTokens != null && !deviceTokens.isEmpty();
    }

    public boolean hasTopic() {
        return topic != null && !topic.isEmpty();
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    public boolean isScheduled() {
        return scheduledFor != null && scheduledFor.isAfter(LocalDateTime.now());
    }

    public boolean hasCustomSound() {
        return sound != null && !sound.equals("default");
    }

    public int getEstimatedReach() {
        if (hasDeviceTokens()) {
            return deviceTokens.size();
        }
        return 0; // Topic/condition reach would need external calculation
    }
}