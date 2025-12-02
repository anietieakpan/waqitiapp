package com.waqiti.common.security.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Security alert for immediate attention
 */
@Data
@Builder
@Jacksonized
public class SecurityAlert {
    private String alertId;
    private String alertType;
    private String severity;
    private String title;
    private String description;
    private String userId;
    private String source;
    private List<String> affectedResources;
    private Map<String, Object> alertData;
    private String status;
    private boolean requiresImmediateAction;
    private String assignedTo;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant createdAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant acknowledgedAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant resolvedAt;

    public static class SecurityAlertBuilder {
        public SecurityAlertBuilder event(com.waqiti.common.security.SecurityEvent event) {
            if (event != null) {
                this.userId = event.getUserId() != null ? event.getUserId().toString() : null;
                this.source = event.getEventType();
                this.createdAt = event.getTimestamp();
            }
            return this;
        }
    }
}