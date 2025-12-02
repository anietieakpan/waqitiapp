package com.waqiti.transaction.model;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a compensation action that can be executed to rollback a transaction step
 */
@Data
@Builder
public class CompensationAction {
    private String actionId;
    private ActionType actionType;
    private String targetService;
    private String targetResourceId;
    private Map<String, Object> compensationData;
    private int priority;
    private boolean retryable;
    private int maxRetries;
    private Duration retryDelay;
    private String reason;
    
    // Legacy fields for backward compatibility
    private String name;
    private String service;
    private Runnable action;
    private Duration timeout;
    private int retryCount;
    private boolean critical; // If true, compensation must succeed
    
    public enum ActionType {
        WALLET_BALANCE_REVERSAL,
        FEE_REVERSAL,
        LEDGER_REVERSAL,
        SUBSIDIARY_LEDGER_UPDATE,
        EXTERNAL_SYSTEM_REVERSAL,
        NOTIFICATION,
        AUDIT_LOG,
        CACHE_INVALIDATION
    }
    
    public enum CompensationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        ALREADY_COMPLETED,
        FAILED,
        PARTIAL,
        COMPLETED_WITH_WARNING,
        CIRCUIT_BREAKER_OPEN,
        QUEUED_FOR_RETRY,
        MANUAL_INTERVENTION,
        UNKNOWN
    }
    
    @Data
    @Builder
    public static class CompensationResult {
        private String actionId;
        private CompensationStatus status;
        private String message;
        private String errorMessage;
        private String warningMessage;
        private Map<String, Object> metadata;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private boolean retryable;
    }
}