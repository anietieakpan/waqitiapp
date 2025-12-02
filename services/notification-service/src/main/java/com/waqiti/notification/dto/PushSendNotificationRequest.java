package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSendNotificationRequest {
    
    @NotBlank(message = "Notification type is required")
    private String type;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Body is required")
    private String body;
    
    private String imageUrl;
    
    @Builder.Default
    private Map<String, String> data = new HashMap<>();
    
    @Builder.Default
    private String priority = "default"; // default, high
    
    private String sound; // custom sound file
    
    private Integer badge; // iOS badge number
    
    private Integer ttl; // Time to live in seconds
    
    private String collapseKey; // Android collapse key
    
    private String threadId; // iOS thread ID for grouping
    
    private String clickAction; // Action to perform on click
    
    private boolean contentAvailable; // iOS background notification
    
    private boolean mutableContent; // iOS notification service extension
}