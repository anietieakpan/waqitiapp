package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String id;
    private String userId;
    private String type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private Instant timestamp;
    private String priority;
    private boolean read;
    private String category;
    
    public enum NotificationType {
        PAYMENT_RECEIVED,
        PAYMENT_SENT,
        PAYMENT_REQUEST,
        PAYMENT_APPROVED,
        PAYMENT_DECLINED,
        FRIEND_REQUEST,
        SYSTEM_ALERT,
        SECURITY_ALERT,
        PROMOTION
    }
    
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }
}