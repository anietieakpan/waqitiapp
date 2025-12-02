package com.waqiti.audit.client;

import java.util.List;

public record IncidentResponse(
    String incidentId,
    String status,
    String assignedTo,
    String priority,
    List<String> actions
) {}
