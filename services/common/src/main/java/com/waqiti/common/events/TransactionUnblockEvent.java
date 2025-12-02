package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a blocked transaction is unblocked
 */
@Data
@Builder
public class TransactionUnblockEvent {
    
    public enum UnblockReason {
        FALSE_POSITIVE_CLEARED,
        MANUAL_REVIEW_APPROVED,
        AUTO_REVIEW_PASSED,
        COMPLIANCE_OVERRIDE,
        RISK_REASSESSMENT,
        MONITORING_COMPLETED
    }
    
    private UUID userId;
    private UUID transactionId;
    private UnblockReason unblockReason;
    private String unblockDescription;
    private String clearedBy;
    private String originalScreeningId;
    private LocalDateTime unblockedAt;
    private boolean notifyUser;
    private String complianceNotes;
    
    @Builder.Default
    private String eventType = "TRANSACTION_UNBLOCKED";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}