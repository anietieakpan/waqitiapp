package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionAction {

    private String actionType;
    
    private String actionDescription;
    
    private boolean successful;
    
    @Builder.Default
    private LocalDateTime executedAt = LocalDateTime.now();
    
    private String executedBy;
    
    private Long executionTimeMs;
    
    private String result;
    
    private String failureReason;
    
    private BigDecimal impactAmount;
    
    private String impactDescription;
    
    private Map<String, Object> actionDetails;
    
    private ActionStatus status;
    
    private String rollbackProcedure;

    public enum ActionStatus {
        PENDING("Pending Execution"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed Successfully"),
        FAILED("Failed"),
        ROLLED_BACK("Rolled Back"),
        PARTIAL("Partially Completed");

        private final String description;

        ActionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isSuccessful() {
        return successful && ActionStatus.COMPLETED.equals(status);
    }

    public boolean isFailed() {
        return !successful || ActionStatus.FAILED.equals(status);
    }

    public boolean isPending() {
        return ActionStatus.PENDING.equals(status);
    }

    public boolean isInProgress() {
        return ActionStatus.IN_PROGRESS.equals(status);
    }

    public boolean isRolledBack() {
        return ActionStatus.ROLLED_BACK.equals(status);
    }

    public boolean hasImpact() {
        return impactAmount != null && impactAmount.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean hasPositiveImpact() {
        return hasImpact() && impactAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasNegativeImpact() {
        return hasImpact() && impactAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean canBeRolledBack() {
        return rollbackProcedure != null && !rollbackProcedure.isEmpty() && 
               isSuccessful();
    }

    public boolean wasQuickExecution() {
        return executionTimeMs != null && executionTimeMs < 1000; // Less than 1 second
    }

    public boolean wasSlowExecution() {
        return executionTimeMs != null && executionTimeMs > 60000; // More than 1 minute
    }
}