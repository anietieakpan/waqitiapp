package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a delayed transaction is resumed for processing
 */
@Data
@Builder
public class TransactionResumeEvent {
    
    private UUID transactionId;
    private String resumeReason;
    private LocalDateTime resumedAt;
    private String originalDelayId;
    private String resumingSystem;
    private boolean wasAutoResumed;
    private String complianceApproval;
    
    @Builder.Default
    private String eventType = "TRANSACTION_RESUMED";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}