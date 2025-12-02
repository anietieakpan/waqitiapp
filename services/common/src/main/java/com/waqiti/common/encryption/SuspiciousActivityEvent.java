package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Audit event for suspicious encryption-related activity
 */
@Data
@Builder
public class SuspiciousActivityEvent {
    private String activityType;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> indicators;
    private String description;
    private String sourceIp;
    private String userAgent;
    private DataContext context;
}