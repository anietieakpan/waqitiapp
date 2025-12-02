package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification event for sanctions clearance
 */
@Data
@Builder
public class SanctionsClearanceNotification {
    
    private UUID userId;
    private String screeningId;
    private String clearedBy;
    private String clearanceReason;
    private LocalDateTime clearedAt;
    private String notificationType;
    private String priority;
    private boolean requiresAcknowledgment;
    private String caseId;
    
    @Builder.Default
    private String eventType = "SANCTIONS_CLEARANCE";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}