package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents an implementation plan for database optimization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImplementationPlan {
    
    private String planId;
    private String strategyId;
    private String description;
    private List<ImplementationStep> steps;
    private LocalDateTime scheduledTime;
    private long estimatedDurationMs;
    private PlanStatus status;
    private String approvedBy;
    private LocalDateTime approvalTime;
    private Map<String, Object> prerequisites;
    private List<String> rollbackSteps;
    private double riskScore;
    private String environmentTarget;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImplementationStep {
        private int stepNumber;
        private String description;
        private String command;
        private StepType type;
        private long estimatedDurationMs;
        private boolean canRollback;
        private String rollbackCommand;
        private Map<String, Object> parameters;
        private List<String> dependencies;
        
        public enum StepType {
            DDL,
            DML,
            INDEX_CREATE,
            INDEX_DROP,
            CONFIG_CHANGE,
            RESTART_REQUIRED,
            VALIDATION
        }
    }
    
    public enum PlanStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }
}