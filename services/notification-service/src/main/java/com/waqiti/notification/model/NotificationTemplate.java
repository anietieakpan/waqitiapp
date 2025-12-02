package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Model for notification templates used across different channels
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {
    
    private String id;
    private String name;
    private String title;
    private String message;
    private String description;
    private String category;
    private String channel; // EMAIL, SMS, PUSH, IN_APP, WHATSAPP
    private String locale;
    private String subject; // For email templates
    private String htmlContent; // For email templates
    private String textContent; // For email and SMS templates
    private Map<String, Object> metadata;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public NotificationTemplate(String name, String title, String message) {
        this.name = name;
        this.title = title;
        this.message = message;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return !active || (updatedAt != null && updatedAt.isBefore(LocalDateTime.now().minusDays(30)));
    }
    
    public boolean supports(String channel) {
        return this.channel == null || this.channel.equalsIgnoreCase(channel) || "ALL".equalsIgnoreCase(this.channel);
    }
    
    public String getTemplateFor(String channel) {
        switch (channel.toUpperCase()) {
            case "EMAIL":
                return htmlContent != null ? htmlContent : message;
            case "SMS":
            case "WHATSAPP":
                return textContent != null ? textContent : message;
            case "PUSH":
            case "IN_APP":
                return message;
            default:
                return message;
        }
    }
}