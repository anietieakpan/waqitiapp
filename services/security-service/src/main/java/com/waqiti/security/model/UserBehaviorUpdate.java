package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * User Behavior Update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorUpdate {

    private Map<String, String> loginLocation;
    private Instant loginTime;
    private String deviceId;
    private String userAgent;
    private String ipAddress;
    private String authMethod;
    private Boolean successful;
    private Map<String, Object> additionalContext;
}
