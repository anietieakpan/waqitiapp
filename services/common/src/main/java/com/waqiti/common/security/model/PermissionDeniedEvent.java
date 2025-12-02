package com.waqiti.common.security.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;

/**
 * Permission denied security event
 */
@Data
@Builder
@Jacksonized
public class PermissionDeniedEvent {
    private String eventId;
    private String userId;
    private String resource;
    private String action;
    private String reason;
    private String ipAddress;
    private String userAgent;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant timestamp;
    
    private Map<String, Object> context;
    private String severity;
    private boolean requiresInvestigation;
    private String requiredRole;

    public String getRequiredRole() {
        return requiredRole;
    }
}