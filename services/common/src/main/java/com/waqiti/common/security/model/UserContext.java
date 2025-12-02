package com.waqiti.common.security.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * User context for security decisions
 */
@Data
@Builder
@Jacksonized
public class UserContext {
    private String userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private Map<String, Object> attributes;
    private Instant loginTime;
    private Instant lastActivity;
    private String riskLevel;
}