package com.waqiti.audit.client;

import java.util.Map;

public record SecurityIncidentRequest(
    String incidentType,
    String severity,
    String description,
    String affectedResource,
    String userId,
    String sourceIp,
    Map<String, Object> details
) {}
