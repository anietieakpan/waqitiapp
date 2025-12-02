package com.waqiti.audit.client;

import java.util.List;
import java.util.Map;

public record UserSecurityProfile(
    String userId,
    List<String> roles,
    List<String> permissions,
    String securityLevel,
    boolean mfaEnabled,
    String lastLoginIp,
    String lastLoginTime,
    int failedLoginAttempts,
    boolean accountLocked,
    Map<String, Object> attributes
) {}
