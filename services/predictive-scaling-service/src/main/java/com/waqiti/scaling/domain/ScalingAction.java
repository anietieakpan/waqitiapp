package com.waqiti.scaling.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scaling_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScalingAction {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "action_id", unique = true, nullable = false, length = 50)
    private String actionId;
    
    @Column(name = "prediction_id", length = 50)
    private String predictionId;
    
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;
    
    @Column(name = "namespace", length = 100)
    private String namespace;
    
    @Column(name = "resource_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ResourceType resourceType;
    
    @Column(name = "action_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    
    @Column(name = "trigger_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;
    
    @Column(name = "execution_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ExecutionStatus executionStatus = ExecutionStatus.PENDING;
    
    @Column(name = "priority", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Priority priority;
    
    // Current state before action
    @Column(name = "current_replicas", nullable = false)
    private Integer currentReplicas;
    
    @Column(name = "current_cpu_request")
    private String currentCpuRequest;
    
    @Column(name = "current_memory_request")
    private String currentMemoryRequest;
    
    @Column(name = "current_cpu_limit")
    private String currentCpuLimit;
    
    @Column(name = "current_memory_limit")
    private String currentMemoryLimit;
    
    // Target state after action
    @Column(name = "target_replicas", nullable = false)
    private Integer targetReplicas;
    
    @Column(name = "target_cpu_request")
    private String targetCpuRequest;
    
    @Column(name = "target_memory_request")
    private String targetMemoryRequest;
    
    @Column(name = "target_cpu_limit")
    private String targetCpuLimit;
    
    @Column(name = "target_memory_limit")
    private String targetMemoryLimit;
    
    // Scaling parameters
    @Column(name = "scaling_factor")
    private Double scalingFactor;
    
    @Column(name = "scaling_step_size")
    private Integer scalingStepSize;
    
    @Column(name = "max_surge")
    private Integer maxSurge;
    
    @Column(name = "max_unavailable")
    private Integer maxUnavailable;
    
    // Timing and constraints
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
    
    @Column(name = "execute_after")
    private LocalDateTime executeAfter;
    
    @Column(name = "execute_before")
    private LocalDateTime executeBefore;
    
    @Column(name = "cooldown_period_minutes")
    private Integer cooldownPeriodMinutes;
    
    @Column(name = "rollback_timeout_minutes")
    private Integer rollbackTimeoutMinutes;
    
    // Execution details
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    
    // Execution context
    @Column(name = "executor_type", length = 30)
    private String executorType; // KUBERNETES, DOCKER_SWARM, AWS_ECS, etc.
    
    @Column(name = "execution_method", length = 30)
    private String executionMethod; // HPA, VPA, DEPLOYMENT_PATCH, etc.
    
    @Type(type = "jsonb")
    @Column(name = "execution_parameters", columnDefinition = "jsonb")
    private Map<String, Object> executionParameters;
    
    @Type(type = "jsonb")
    @Column(name = "kubernetes_spec", columnDefinition = "jsonb")
    private Map<String, Object> kubernetesSpec;
    
    // Validation and safety
    @Column(name = "dry_run_performed")
    private Boolean dryRunPerformed = false;
    
    @Column(name = "safety_checks_passed")
    private Boolean safetyChecksPassed = false;
    
    @Column(name = "rollback_plan_ready")
    private Boolean rollbackPlanReady = false;
    
    @Type(type = "jsonb")
    @Column(name = "safety_constraints", columnDefinition = "jsonb")
    private Map<String, Object> safetyConstraints;
    
    @Type(type = "jsonb")
    @Column(name = "rollback_plan", columnDefinition = "jsonb")
    private Map<String, Object> rollbackPlan;
    
    // Cost impact
    @Column(name = "current_cost_per_hour", precision = 19, scale = 4)
    private BigDecimal currentCostPerHour;
    
    @Column(name = "target_cost_per_hour", precision = 19, scale = 4)
    private BigDecimal targetCostPerHour;
    
    @Column(name = "cost_impact_per_hour", precision = 19, scale = 4)
    private BigDecimal costImpactPerHour;
    
    @Column(name = "estimated_savings", precision = 19, scale = 4)
    private BigDecimal estimatedSavings;
    
    // Performance impact
    @Column(name = "performance_impact_score")
    private Double performanceImpactScore;
    
    @Column(name = "availability_risk_score")
    private Double availabilityRiskScore;
    
    @Column(name = "sla_compliance_risk")
    private Double slaComplianceRisk;
    
    // Monitoring and feedback
    @Column(name = "pre_action_metrics_captured")
    private Boolean preActionMetricsCaptured = false;
    
    @Column(name = "post_action_metrics_captured")
    private Boolean postActionMetricsCaptured = false;
    
    @Type(type = "jsonb")
    @Column(name = "pre_action_metrics", columnDefinition = "jsonb")
    private Map<String, Object> preActionMetrics;
    
    @Type(type = "jsonb")
    @Column(name = "post_action_metrics", columnDefinition = "jsonb")
    private Map<String, Object> postActionMetrics;
    
    @Column(name = "action_effectiveness_score")
    private Double actionEffectivenessScore;
    
    // Error handling
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    @Type(type = "jsonb")
    @Column(name = "error_details", columnDefinition = "jsonb")
    private Map<String, Object> errorDetails;
    
    // Approval and governance
    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;
    
    @Column(name = "approved_by", length = 100)
    private String approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "approval_reason", columnDefinition = "TEXT")
    private String approvalReason;
    
    // Audit and metadata
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;
    
    @Column(name = "automation_level", length = 20)
    @Enumerated(EnumType.STRING)
    private AutomationLevel automationLevel;
    
    @Type(type = "jsonb")
    @Column(name = "action_metadata", columnDefinition = "jsonb")
    private Map<String, Object> actionMetadata;
    
    @Type(type = "jsonb")
    @Column(name = "execution_logs", columnDefinition = "jsonb")
    private Map<String, Object> executionLogs;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (actionId == null) {
            actionId = "SA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        
        if (scheduledAt == null) {
            scheduledAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Set completion timestamp based on status
        switch (executionStatus) {
            case COMPLETED:
                if (completedAt == null) completedAt = LocalDateTime.now();
                break;
            case FAILED:
                if (failedAt == null) failedAt = LocalDateTime.now();
                break;
            case CANCELLED:
                if (cancelledAt == null) cancelledAt = LocalDateTime.now();
                break;
            case EXECUTING:
                if (startedAt == null) startedAt = LocalDateTime.now();
                break;
        }
        
        // Calculate duration
        if (startedAt != null && completedAt != null) {
            durationSeconds = java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
    }
    
    public enum ResourceType {
        DEPLOYMENT,
        STATEFULSET,
        DAEMONSET,
        REPLICASET,
        HPA,
        VPA,
        PDB,
        SERVICE,
        CONFIGMAP,
        SECRET
    }
    
    public enum ActionType {
        SCALE_UP,
        SCALE_DOWN,
        SCALE_TO_ZERO,
        RIGHT_SIZE,
        OPTIMIZE_RESOURCES,
        UPDATE_LIMITS,
        UPDATE_REQUESTS,
        MIGRATE_NODES,
        RESTART_PODS
    }
    
    public enum TriggerType {
        PREDICTIVE_MODEL,
        REACTIVE_THRESHOLD,
        SCHEDULED_EVENT,
        MANUAL_TRIGGER,
        ANOMALY_DETECTION,
        COST_OPTIMIZATION,
        CAPACITY_PLANNING,
        MAINTENANCE_WINDOW,
        EMERGENCY_RESPONSE
    }
    
    public enum ExecutionStatus {
        PENDING,        // Action is queued
        SCHEDULED,      // Action is scheduled for future execution
        VALIDATING,     // Performing safety checks
        APPROVED,       // Action has been approved
        EXECUTING,      // Action is currently being executed
        COMPLETED,      // Action completed successfully
        FAILED,         // Action failed
        CANCELLED,      // Action was cancelled
        ROLLED_BACK,    // Action was rolled back
        TIMEOUT         // Action timed out
    }
    
    public enum Priority {
        CRITICAL,       // Execute immediately
        HIGH,           // Execute within 5 minutes
        MEDIUM,         // Execute within 15 minutes
        LOW,            // Execute within 30 minutes
        BACKGROUND      // Execute when convenient
    }
    
    public enum AutomationLevel {
        FULLY_AUTOMATED,    // No human intervention required
        SEMI_AUTOMATED,     // Requires approval for execution
        MANUAL,             // Requires manual execution
        ADVISORY_ONLY       // Provides recommendation only
    }
    
    // Business logic methods
    
    public boolean canExecute() {
        return executionStatus == ExecutionStatus.PENDING ||
               executionStatus == ExecutionStatus.SCHEDULED ||
               executionStatus == ExecutionStatus.APPROVED;
    }
    
    public boolean requiresImmediate() {
        return priority == Priority.CRITICAL;
    }
    
    public boolean isScaleUp() {
        return actionType == ActionType.SCALE_UP;
    }
    
    public boolean isScaleDown() {
        return actionType == ActionType.SCALE_DOWN;
    }
    
    public boolean hasSignificantCostImpact() {
        return costImpactPerHour != null && 
               costImpactPerHour.abs().compareTo(new BigDecimal("5.00")) > 0;
    }
    
    public boolean hasHighRisk() {
        return availabilityRiskScore != null && availabilityRiskScore > 0.7;
    }
    
    public boolean isOverdue() {
        return executeBefore != null && LocalDateTime.now().isAfter(executeBefore);
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries && 
               (executionStatus == ExecutionStatus.FAILED || executionStatus == ExecutionStatus.TIMEOUT);
    }
    
    public int getScalingDelta() {
        return targetReplicas - currentReplicas;
    }
    
    public double getScalingPercentage() {
        if (currentReplicas > 0) {
            return ((double) (targetReplicas - currentReplicas) / currentReplicas) * 100.0;
        }
        return 0.0;
    }
    
    public void initiate() {
        this.initiatedAt = LocalDateTime.now();
        this.executionStatus = ExecutionStatus.VALIDATING;
    }
    
    public void startExecution() {
        this.startedAt = LocalDateTime.now();
        this.executionStatus = ExecutionStatus.EXECUTING;
    }
    
    public void complete() {
        this.completedAt = LocalDateTime.now();
        this.executionStatus = ExecutionStatus.COMPLETED;
        calculateDuration();
    }
    
    public void fail(String errorCode, String errorMessage, Map<String, Object> errorDetails) {
        this.failedAt = LocalDateTime.now();
        this.executionStatus = ExecutionStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorDetails = errorDetails;
        calculateDuration();
    }
    
    public void cancel(String reason) {
        this.cancelledAt = LocalDateTime.now();
        this.executionStatus = ExecutionStatus.CANCELLED;
        this.errorMessage = reason;
        calculateDuration();
    }
    
    public void retry() {
        this.retryCount++;
        this.executionStatus = ExecutionStatus.PENDING;
        this.errorMessage = null;
        this.errorCode = null;
        this.errorDetails = null;
    }
    
    public void approve(String approver, String reason) {
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
        this.approvalReason = reason;
        this.executionStatus = ExecutionStatus.APPROVED;
    }
    
    public void schedule(LocalDateTime scheduledTime) {
        this.scheduledAt = scheduledTime;
        this.executionStatus = ExecutionStatus.SCHEDULED;
    }
    
    private void calculateDuration() {
        if (startedAt != null) {
            LocalDateTime endTime = completedAt != null ? completedAt :
                                   failedAt != null ? failedAt :
                                   cancelledAt != null ? cancelledAt : LocalDateTime.now();
            
            this.durationSeconds = java.time.Duration.between(startedAt, endTime).getSeconds();
        }
    }
    
    public Map<String, Object> toExecutionEvent() {
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("actionId", actionId);
        event.put("predictionId", predictionId);
        event.put("serviceName", serviceName);
        event.put("namespace", namespace);
        event.put("actionType", actionType);
        event.put("resourceType", resourceType);
        event.put("currentReplicas", currentReplicas);
        event.put("targetReplicas", targetReplicas);
        event.put("executionStatus", executionStatus);
        event.put("priority", priority);
        event.put("triggerType", triggerType);
        event.put("costImpact", costImpactPerHour);
        event.put("scheduledAt", scheduledAt);
        event.put("executionParameters", executionParameters);
        return event;
    }
}