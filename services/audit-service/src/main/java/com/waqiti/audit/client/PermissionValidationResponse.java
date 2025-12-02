package com.waqiti.audit.client;

import java.util.List;
import java.util.Map;

public record PermissionValidationResponse(
    boolean hasPermission,
    String reason,
    List<String> requiredRoles,
    Map<String, Object> metadata
) {}
