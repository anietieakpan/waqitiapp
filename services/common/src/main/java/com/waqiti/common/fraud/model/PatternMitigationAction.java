package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pattern-specific fraud mitigation actions with advanced coordination
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class PatternMitigationAction {
    
    private String actionId;
    private String patternId;
    private String transactionId;
    private String userId;
    private String sessionId;
    
    // Action configuration
    private PatternActionType actionType;
    private String description;
    private ActionScope scope;
    private ActionTiming timing;
    private Integer priority;
    private ActionSeverity severity;

    // Pattern-specific details
    private String targetPattern;
    private List<String> affectedPatterns;
    private String mitigationStrategy;
    private String patternDisruptionMethod;
    
    // Execution parameters
    private Map<String, Object> actionParameters;
    private String executionPlan;
    private LocalDateTime scheduledExecutionTime;
    private Long estimatedDurationMs;
    private Boolean requiresHumanApproval;
    
    // Coordination and dependencies
    private List<String> dependentActions;
    private List<String> prerequisiteActions;
    private String coordinationGroup;
    private Boolean isCoordinatedAction;
    
    // Status tracking
    private ActionStatus status;
    private LocalDateTime initiatedAt;
    private LocalDateTime executedAt;
    private LocalDateTime completedAt;
    private String executedBy;
    private String executionResult;
    
    // Effectiveness tracking
    private Double effectivenessScore;
    private Boolean patternSuppressed;
    private LocalDateTime suppressionVerifiedAt;
    private String suppressionMethod;
    private Long suppressionDurationMs;
    
    // Impact assessment
    private BusinessImpact businessImpact;
    private CustomerImpact customerImpact;
    private Map<String, String> impactMetrics;
    private List<String> affectedSystems;
    
    // Monitoring and alerts
    private Boolean requiresContinuousMonitoring;
    private String monitoringParameters;
    private List<String> alertConditions;
    private String escalationCriteria;
    
    // Additional metadata
    private Map<String, Object> additionalData;
    private String notes;
    private List<String> tags;
    
    /**
     * Pattern-specific action types
     */
    public enum PatternActionType {
        // Velocity pattern actions
        APPLY_VELOCITY_LIMITS,
        IMPLEMENT_COOLING_PERIOD,
        THROTTLE_TRANSACTION_RATE,
        
        // Amount pattern actions
        SET_AMOUNT_THRESHOLDS,
        FLAG_STRUCTURED_AMOUNTS,
        APPLY_CUMULATIVE_LIMITS,
        
        // Behavioral pattern actions
        REQUIRE_BEHAVIORAL_CHALLENGE,
        RESET_BEHAVIORAL_BASELINE,
        ENABLE_BEHAVIORAL_MONITORING,
        
        // Network pattern actions
        ISOLATE_NETWORK_NODES,
        BLOCK_NETWORK_CLUSTER,
        MONITOR_NETWORK_PROPAGATION,
        
        // Temporal pattern actions
        RESTRICT_TIME_WINDOWS,
        RANDOMIZE_PROCESSING_DELAYS,
        IMPLEMENT_TIME_LOCKS,
        
        // Device pattern actions
        QUARANTINE_DEVICE,
        REQUIRE_DEVICE_VERIFICATION,
        INVALIDATE_DEVICE_TRUST,
        
        // Location pattern actions
        GEOFENCE_RESTRICTIONS,
        REQUIRE_LOCATION_VERIFICATION,
        FLAG_LOCATION_ANOMALIES,
        
        // Multi-pattern actions
        COMPREHENSIVE_LOCKDOWN,
        PATTERN_DISRUPTION_PROTOCOL,
        COORDINATED_SUPPRESSION
    }
    
    /**
     * Action execution scope
     */
    public enum ActionScope {
        TRANSACTION_SPECIFIC,  // Single transaction
        USER_SPECIFIC,         // Single user account
        DEVICE_SPECIFIC,       // Single device
        SESSION_SPECIFIC,      // Single session
        NETWORK_WIDE,          // Entire network segment
        SYSTEM_WIDE,           // Platform-wide
        GLOBAL                 // All systems/regions
    }
    
    /**
     * Action timing requirements
     */
    public enum ActionTiming {
        IMMEDIATE,       // Execute immediately
        SCHEDULED,       // Execute at specific time
        CONDITIONAL,     // Execute when conditions met
        PROGRESSIVE,     // Execute in stages
        REACTIVE,        // Execute in response to events
        PREVENTIVE       // Execute before pattern completion
    }
    
    /**
     * Action severity levels
     */
    public enum ActionSeverity {
        INFORMATIONAL,   // Logging/monitoring only
        LOW,            // Minor restrictions
        MEDIUM,         // Moderate restrictions
        HIGH,           // Significant restrictions
        CRITICAL,       // Major limitations
        EMERGENCY       // Complete lockdown
    }
    
    /**
     * Action execution status
     */
    public enum ActionStatus {
        CREATED,
        APPROVED,
        SCHEDULED,
        EXECUTING,
        PARTIALLY_COMPLETE,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED,
        SUPERSEDED
    }
    
    /**
     * Business impact levels
     */
    public enum BusinessImpact {
        NONE,
        MINIMAL,
        LOW,
        MODERATE,
        HIGH,
        SEVERE
    }
    
    /**
     * Customer impact levels
     */
    public enum CustomerImpact {
        TRANSPARENT,     // No customer impact
        MINOR,          // Slight inconvenience
        NOTICEABLE,     // Clear impact but manageable
        SIGNIFICANT,    // Major inconvenience
        SEVERE,         // Substantial disruption
        BLOCKING        // Complete service block
    }
    
    /**
     * Calculate action effectiveness based on pattern suppression
     */
    public double calculateActionEffectiveness() {
        if (status != ActionStatus.COMPLETED || effectivenessScore != null) {
            return effectivenessScore != null ? effectivenessScore : 0.0;
        }
        
        double effectiveness = 0.5; // Base effectiveness
        
        // Pattern suppression factor
        if (patternSuppressed != null && patternSuppressed) {
            effectiveness += 0.3;
        }
        
        // Execution speed factor
        if (executedAt != null && completedAt != null) {
            long executionTime = java.time.Duration.between(executedAt, completedAt).toMillis();
            if (executionTime < estimatedDurationMs) {
                effectiveness += 0.1;
            }
        }
        
        // Coordination factor
        if (isCoordinatedAction != null && isCoordinatedAction) {
            effectiveness += 0.1;
        }
        
        // Business impact factor (less impact = more effective)
        if (businessImpact == BusinessImpact.NONE || businessImpact == BusinessImpact.MINIMAL) {
            effectiveness += 0.1;
        }
        
        return Math.min(1.0, effectiveness);
    }
    
    /**
     * Check if action requires immediate execution
     */
    public boolean requiresImmediateExecution() {
        return timing == ActionTiming.IMMEDIATE ||
               severity == ActionSeverity.EMERGENCY ||
               severity == ActionSeverity.CRITICAL;
    }
    
    /**
     * Check if action can be executed autonomously
     */
    public boolean canExecuteAutonomously() {
        return requiresHumanApproval == null || !requiresHumanApproval;
    }
    
    /**
     * Get estimated completion time
     */
    public LocalDateTime getEstimatedCompletionTime() {
        if (scheduledExecutionTime != null && estimatedDurationMs != null) {
            return scheduledExecutionTime.plusNanos(estimatedDurationMs * 1_000_000);
        }
        
        if (executedAt != null && estimatedDurationMs != null) {
            return executedAt.plusNanos(estimatedDurationMs * 1_000_000);
        }
        
        return null;
    }
    
    /**
     * Generate action execution summary
     */
    public String generateExecutionSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Pattern Mitigation Action: ").append(actionType).append("\n");
        summary.append("Target Pattern: ").append(targetPattern).append("\n");
        summary.append("Scope: ").append(scope).append("\n");
        summary.append("Severity: ").append(severity).append("\n");
        summary.append("Status: ").append(status).append("\n");
        
        if (patternSuppressed != null && patternSuppressed) {
            summary.append("Pattern Successfully Suppressed\n");
        }
        
        if (effectivenessScore != null) {
            summary.append("Effectiveness: ").append(String.format("%.2f", effectivenessScore)).append("\n");
        }
        
        if (businessImpact != null) {
            summary.append("Business Impact: ").append(businessImpact).append("\n");
        }
        
        if (customerImpact != null) {
            summary.append("Customer Impact: ").append(customerImpact).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Get recommended monitoring parameters for this action
     */
    public Map<String, Object> getRecommendedMonitoringParameters() {
        Map<String, Object> monitoring = Map.of(
            "monitorDurationMinutes", getMonitoringDuration(),
            "checkIntervalSeconds", getCheckInterval(),
            "successMetrics", getSuccessMetrics(),
            "failureIndicators", getFailureIndicators()
        );
        
        return monitoring;
    }
    
    /**
     * Get monitoring duration based on action type and severity
     */
    private int getMonitoringDuration() {
        if (severity == ActionSeverity.EMERGENCY) return 1440; // 24 hours
        if (severity == ActionSeverity.CRITICAL) return 720;   // 12 hours
        if (severity == ActionSeverity.HIGH) return 360;       // 6 hours
        if (severity == ActionSeverity.MEDIUM) return 120;     // 2 hours
        return 60; // 1 hour
    }
    
    /**
     * Get check interval based on action urgency
     */
    private int getCheckInterval() {
        if (requiresImmediateExecution()) return 30; // 30 seconds
        if (timing == ActionTiming.PREVENTIVE) return 60; // 1 minute
        return 300; // 5 minutes
    }
    
    /**
     * Get success metrics for monitoring
     */
    private List<String> getSuccessMetrics() {
        return List.of(
            "pattern_suppression_rate",
            "false_positive_rate",
            "execution_time_compliance",
            "business_impact_minimization"
        );
    }
    
    /**
     * Get failure indicators for monitoring
     */
    private List<String> getFailureIndicators() {
        return List.of(
            "pattern_persistence",
            "execution_timeout",
            "system_errors",
            "excessive_customer_impact"
        );
    }
}