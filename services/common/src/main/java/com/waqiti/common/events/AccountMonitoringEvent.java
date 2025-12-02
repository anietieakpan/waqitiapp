package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when account monitoring is enabled
 */
@Data
@Builder
public class AccountMonitoringEvent {
    
    private UUID userId;
    private String restrictionId;
    private String monitoringLevel;
    private String reason;
    private LocalDateTime startedAt;
    private LocalDateTime endDate;
    private boolean notifyCompliance;
    
    @Builder.Default
    private String eventType = "ACCOUNT_MONITORING_ENABLED";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}