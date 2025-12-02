package com.waqiti.reconciliation.command;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command pattern for reconciliation operations
 * Ensures all reconciliation requests are properly tracked and audited
 */
@Data
@Builder
public class ReconciliationCommand {
    
    @NonNull
    @Builder.Default
    private final String commandId = UUID.randomUUID().toString();
    
    @NonNull
    private final ReconciliationType type;
    
    @NonNull
    @Builder.Default
    private final Instant requestedAt = Instant.now();
    
    @NonNull
    private final String initiatedBy;
    
    private final LocalDateTime cutoffTime;
    
    private final ReconciliationScope scope;
    
    @Builder.Default
    private final boolean forceRun = false;
    
    @Builder.Default
    private final int priority = 5; // 1-10, 1 being highest
    
    private final String reason;
    
    private final ReconciliationContext context;
    
    public enum ReconciliationType {
        STARTUP_RECONCILIATION,
        SCHEDULED_RECONCILIATION,
        MANUAL_RECONCILIATION,
        EMERGENCY_RECONCILIATION,
        PARTIAL_RECONCILIATION,
        FULL_RECONCILIATION
    }
    
    public enum ReconciliationScope {
        ALL_TRANSACTIONS,
        PENDING_ONLY,
        FAILED_ONLY,
        DATE_RANGE,
        SPECIFIC_PROVIDERS,
        HIGH_VALUE_ONLY
    }
    
    @Data
    @Builder
    public static class ReconciliationContext {
        private final String correlationId;
        private final String sourceSystem;
        private final boolean dryRun;
        private final boolean generateReport;
        private final boolean notifyOnCompletion;
        private final String[] notificationRecipients;
    }
}