package com.waqiti.saga.migration;

import com.waqiti.common.saga.SagaStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Migration utility to transition from service-specific SagaStatus 
 * to the common SagaStatus enum which includes COMPENSATION_FAILED.
 * 
 * This class provides mapping and migration logic to ensure backward
 * compatibility during the transition period.
 */
@Slf4j
@Component
public class SagaStatusMigration {
    
    /**
     * Maps old status values to new common SagaStatus
     * This handles the missing COMPENSATION_FAILED status in the old enum
     */
    public SagaStatus mapToCommonStatus(String oldStatus) {
        if (oldStatus == null) {
            return SagaStatus.INITIATED;
        }
        
        try {
            // Direct mapping for statuses that exist in both
            return SagaStatus.valueOf(oldStatus);
        } catch (IllegalArgumentException e) {
            // Handle any legacy status values that might exist in the database
            log.warn("Unknown saga status encountered: {}. Mapping to FAILED.", oldStatus);
            return SagaStatus.FAILED;
        }
    }
    
    /**
     * Determines if a failed saga should be marked as COMPENSATION_FAILED
     * based on the saga context and failure reason
     */
    public SagaStatus determineFailureStatus(boolean compensationAttempted, 
                                            boolean compensationSuccessful,
                                            String failureReason) {
        if (!compensationAttempted) {
            return SagaStatus.FAILED;
        }
        
        if (compensationSuccessful) {
            return SagaStatus.COMPENSATED;
        }
        
        // This is the critical fix - properly tracking compensation failures
        if (failureReason != null && 
            (failureReason.contains("compensation") || 
             failureReason.contains("rollback"))) {
            return SagaStatus.COMPENSATION_FAILED;
        }
        
        return SagaStatus.FAILED;
    }
    
    /**
     * Validates status transitions according to the saga state machine
     */
    public boolean isValidTransition(SagaStatus currentStatus, SagaStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }
        
        // Terminal states cannot transition
        if (currentStatus.isTerminal()) {
            log.warn("Attempted transition from terminal state: {} to {}", 
                    currentStatus, newStatus);
            return false;
        }
        
        // Define valid transitions
        return switch (currentStatus) {
            case INITIATED -> 
                newStatus == SagaStatus.RUNNING || 
                newStatus == SagaStatus.FAILED;
                
            case RUNNING -> 
                newStatus == SagaStatus.COMPLETED || 
                newStatus == SagaStatus.FAILED ||
                newStatus == SagaStatus.COMPENSATING;
                
            case COMPENSATING -> 
                newStatus == SagaStatus.COMPENSATED || 
                newStatus == SagaStatus.COMPENSATION_FAILED;
                
            case FAILED -> 
                newStatus == SagaStatus.COMPENSATING;
                
                
            default -> false;
        };
    }
}