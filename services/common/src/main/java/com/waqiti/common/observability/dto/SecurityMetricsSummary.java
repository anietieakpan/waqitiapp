package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Security metrics summary for observability dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityMetricsSummary {
    private long totalSecurityEvents;
    private long failedAuthentications;
    private long successfulAuthentications;
    private long activeThreats;
    private long blockedAttempts;
    private double authSuccessRate;
    private Instant timestamp;

    // Additional fields for ObservabilityDashboard
    private long activeSecurityAlerts;
    private double authenticationFailureRate;
    private long blockedIPAddresses;
    private long suspendedAccounts;
}