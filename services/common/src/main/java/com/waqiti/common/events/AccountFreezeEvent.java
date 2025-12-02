package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when an account is frozen for compliance reasons
 */
@Data
@Builder
public class AccountFreezeEvent {
    
    public enum FreezeSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    private UUID userId;
    private String freezeId;
    private AccountFreezeRequestEvent.FreezeReason freezeReason;
    private AccountFreezeRequestEvent.FreezeScope freezeScope;
    private FreezeSeverity severity;
    private String description;
    private LocalDateTime frozenAt;
    private String freezingSystem;
    private boolean requiresManualReview;
    private LocalDateTime expirationDate;
    private String complianceCaseId;
    
    @Builder.Default
    private String eventType = "ACCOUNT_FROZEN";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}