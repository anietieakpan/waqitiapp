package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent {
    @NotBlank
    private String eventType;
    
    private String userId;
    private Instant timestamp;
    private Map<String, Object> data;
    private String sessionId;
    private String platform;
    private String version;
    private String userAgent;
    
    public enum EventType {
        PAGE_VIEW,
        BUTTON_CLICK,
        PAYMENT_INITIATED,
        PAYMENT_COMPLETED,
        USER_LOGIN,
        USER_LOGOUT,
        FEATURE_USED,
        ERROR_OCCURRED
    }
}