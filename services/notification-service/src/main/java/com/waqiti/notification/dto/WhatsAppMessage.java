package com.waqiti.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WhatsApp message DTO
 */
@Data
@Builder
public class WhatsAppMessage {
    
    private String phoneNumber;
    private MessageType messageType;
    private String content;
    private String templateName;
    private Map<String, String> templateParameters;
    private String correlationId;
    private Priority priority;
    private LocalDateTime scheduledAt;
    private Map<String, Object> metadata;
    
    public enum MessageType {
        TEXT,
        TEMPLATE,
        MEDIA,
        INTERACTIVE
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}