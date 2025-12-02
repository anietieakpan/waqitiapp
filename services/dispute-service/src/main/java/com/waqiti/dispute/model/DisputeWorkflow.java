package com.waqiti.dispute.model;

import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeEvidence;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispute workflow management
 */
@Data
public class DisputeWorkflow {
    
    private String disputeId;
    private Dispute dispute;
    private List<WorkflowStage> stages = new ArrayList<>();
    private int currentStageIndex = 0;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private WorkflowStatus status = WorkflowStatus.NOT_STARTED;
    private Map<String, Object> context = new ConcurrentHashMap<>();
    private List<DisputeEvidence> collectedEvidence = new ArrayList<>();
    
    public DisputeWorkflow(Dispute dispute) {
        this.disputeId = dispute.getId();
        this.dispute = dispute;
    }
    
    /**
     * Add workflow stage
     */
    public void addStage(String name, int durationHours) {
        WorkflowStage stage = new WorkflowStage(name, durationHours);
        stages.add(stage);
    }
    
    /**
     * Start workflow
     */
    public void start() {
        if (status != WorkflowStatus.NOT_STARTED) {
            throw new IllegalStateException("Workflow already started");
        }
        
        startedAt = LocalDateTime.now();
        status = WorkflowStatus.IN_PROGRESS;
        
        if (!stages.isEmpty()) {
            stages.get(0).start();
        }
    }
    
    /**
     * Move to next stage
     */
    public boolean moveToNextStage() {
        if (currentStageIndex >= stages.size() - 1) {
            return false; // No more stages
        }
        
        // Complete current stage
        WorkflowStage currentStage = stages.get(currentStageIndex);
        currentStage.complete();
        
        // Move to next stage
        currentStageIndex++;
        WorkflowStage nextStage = stages.get(currentStageIndex);
        nextStage.start();
        
        return true;
    }
    
    /**
     * Complete workflow
     */
    public void complete() {
        if (currentStageIndex < stages.size()) {
            stages.get(currentStageIndex).complete();
        }
        
        completedAt = LocalDateTime.now();
        status = WorkflowStatus.COMPLETED;
    }
    
    /**
     * Cancel workflow
     */
    public void cancel() {
        if (currentStageIndex < stages.size()) {
            stages.get(currentStageIndex).cancel();
        }
        
        completedAt = LocalDateTime.now();
        status = WorkflowStatus.CANCELLED;
    }
    
    /**
     * Add evidence to workflow
     */
    public void addEvidence(DisputeEvidence evidence) {
        collectedEvidence.add(evidence);
        context.put("evidence_count", collectedEvidence.size());
    }
    
    /**
     * Get current stage
     */
    public WorkflowStage getCurrentStage() {
        if (currentStageIndex < stages.size()) {
            return stages.get(currentStageIndex);
        }
        return null;
    }
    
    /**
     * Check if workflow is overdue
     */
    public boolean isOverdue() {
        WorkflowStage currentStage = getCurrentStage();
        return currentStage != null && currentStage.isOverdue();
    }
    
    /**
     * Get workflow progress percentage
     */
    public double getProgressPercentage() {
        if (stages.isEmpty()) {
            return 0.0;
        }
        
        double completedStages = 0;
        for (int i = 0; i < currentStageIndex; i++) {
            completedStages++;
        }
        
        // Add partial progress of current stage
        if (currentStageIndex < stages.size()) {
            WorkflowStage currentStage = stages.get(currentStageIndex);
            completedStages += currentStage.getProgressPercentage() / 100.0;
        }
        
        return (completedStages / stages.size()) * 100.0;
    }
    
    /**
     * Workflow stage
     */
    @Data
    public static class WorkflowStage {
        private String name;
        private int durationHours;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime deadline;
        private StageStatus status = StageStatus.PENDING;
        private Map<String, Object> stageData = new HashMap<>();
        
        public WorkflowStage(String name, int durationHours) {
            this.name = name;
            this.durationHours = durationHours;
        }
        
        public void start() {
            startedAt = LocalDateTime.now();
            deadline = startedAt.plusHours(durationHours);
            status = StageStatus.IN_PROGRESS;
        }
        
        public void complete() {
            completedAt = LocalDateTime.now();
            status = StageStatus.COMPLETED;
        }
        
        public void cancel() {
            completedAt = LocalDateTime.now();
            status = StageStatus.CANCELLED;
        }
        
        public boolean isOverdue() {
            return status == StageStatus.IN_PROGRESS && 
                   LocalDateTime.now().isAfter(deadline);
        }
        
        public double getProgressPercentage() {
            if (status == StageStatus.COMPLETED) {
                return 100.0;
            }
            
            if (status == StageStatus.IN_PROGRESS && startedAt != null) {
                long elapsed = java.time.Duration.between(startedAt, LocalDateTime.now()).toHours();
                return Math.min(100.0, (double) elapsed / durationHours * 100.0);
            }
            
            return 0.0;
        }
    }
    
    public enum WorkflowStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        FAILED
    }
    
    public enum StageStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        SKIPPED
    }
}