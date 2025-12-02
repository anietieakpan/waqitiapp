package com.waqiti.audit.client;

import java.util.Map;

public record SecurityPolicy(
    String policyId,
    String policyName,
    String policyType,
    boolean active,
    Map<String, Object> rules
) {}
