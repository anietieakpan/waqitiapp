package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a transaction is delayed for compliance or risk reasons
 */
@Data
@Builder
public class TransactionDelayEvent {
    
    private UUID transactionId;
    private String delayReason;
    private LocalDateTime delayedAt;
    private LocalDateTime executeAt;
    private String delayingSystem;
    private String delayId;
    private boolean notifyUser;
    private String complianceNotes;
    
    @Builder.Default
    private String eventType = "TRANSACTION_DELAYED";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}