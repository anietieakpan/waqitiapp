package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for notification history tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationHistory {

    private String id;
    private String userId;
    private String notificationId;
    private String templateCode;
    private String notificationType;
    private String deliveryChannel;
    
    private String status;
    private String title;
    private String message;
    private Map<String, Object> parameters;
    private Map<String, Object> metadata;
    
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    
    private String failureReason;
    private int retryCount;
    
    /**
     * Gets the user ID as String
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Gets the notification status
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Gets the status with fallback parameter
     */
    public String getStatus(String defaultStatus) {
        return status != null ? status : defaultStatus;
    }
}