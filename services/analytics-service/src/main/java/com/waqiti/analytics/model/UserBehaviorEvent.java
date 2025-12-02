package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * User behavior event for analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorEvent {
    
    private String eventId;
    private String userId;
    private String eventType;
    private String action;
    private String page;
    private String source;
    private String deviceId;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private Instant timestamp;
    private Map<String, Object> properties;
}