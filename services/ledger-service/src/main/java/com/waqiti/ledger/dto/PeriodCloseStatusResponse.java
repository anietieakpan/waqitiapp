package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real-time status response for an ongoing or completed period close process.
 * This DTO provides detailed information about the current state of the 
 * period close operation, including progress, completion status, and any issues.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseStatusResponse {
    
    /**
     * Unique identifier for the period close process
     */
    private UUID periodCloseId;
    
    /**
     * Period being closed
     */
    private UUID periodId;
    
    /**
     * Period information
     */
    private PeriodInfo periodInfo;
    
    /**
     * Overall status of the period close process
     */
    private String overallStatus;
    
    /**
     * Current phase of the close process
     */
    private String currentPhase;
    
    /**
     * Overall progress percentage (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * Progress information for each phase
     */
    private Map<String, PhaseProgress> phaseProgress;
    
    /**
     * When the period close process was initiated
     */
    private LocalDateTime processStartedAt;
    
    /**
     * When the period close process completed (if completed)
     */
    private LocalDateTime processCompletedAt;
    
    /**
     * Estimated completion time (if still in progress)
     */
    private LocalDateTime estimatedCompletionAt;
    
    /**
     * Elapsed time since process started (in milliseconds)
     */
    private Long elapsedTimeMs;
    
    /**
     * Estimated remaining time (in milliseconds, if still in progress)
     */
    private Long estimatedRemainingTimeMs;
    
    /**
     * User who initiated the period close
     */
    private String initiatedBy;
    
    /**
     * Current processor or system handling the close
     */
    private String currentProcessor;
    
    /**
     * Whether the process is currently running
     */
    private boolean isRunning;
    
    /**
     * Whether the process is paused
     */
    private boolean isPaused;
    
    /**
     * Whether the process can be cancelled
     */
    private boolean canBeCancelled;
    
    /**
     * Whether the process can be resumed (if paused)
     */
    private boolean canBeResumed;
    
    /**
     * List of completed activities
     */
    private List<CompletedActivity> completedActivities;
    
    /**
     * List of activities currently in progress
     */
    private List<ActivityInProgress> activitiesInProgress;
    
    /**
     * List of pending activities
     */
    private List<PendingActivity> pendingActivities;
    
    /**
     * Current activity being processed
     */
    private ActivityInProgress currentActivity;
    
    /**
     * Performance metrics for the close process
     */
    private CloseProcessMetrics processMetrics;
    
    /**
     * Any errors encountered during the process
     */
    private List<ProcessError> processErrors;
    
    /**
     * Any warnings generated during the process
     */
    private List<ProcessWarning> processWarnings;
    
    /**
     * Validation results (if validation phase completed)
     */
    private PeriodCloseValidation validationResults;
    
    /**
     * Results summary (if process completed)
     */
    private PeriodCloseResultSummary resultSummary;
    
    /**
     * Resource utilization information
     */
    private ResourceUtilization resourceUtilization;
    
    /**
     * Dependencies that need to be resolved
     */
    private List<ProcessDependency> pendingDependencies;
    
    /**
     * Manual interventions required
     */
    private List<ManualIntervention> manualInterventions;
    
    /**
     * Notifications sent during the process
     */
    private List<ProcessNotification> notifications;
    
    /**
     * Audit trail of the close process
     */
    private List<ProcessAuditEntry> processAuditTrail;
    
    /**
     * Configuration used for this close process
     */
    private CloseProcessConfiguration processConfiguration;
    
    /**
     * Last update timestamp for this status
     */
    private LocalDateTime lastUpdatedAt;
    
    /**
     * Next scheduled check or update time
     */
    private LocalDateTime nextUpdateAt;
    
    /**
     * Additional metadata for the process
     */
    private String metadata;
}

/**
 * Progress information for a specific phase
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PhaseProgress {
    
    /**
     * Phase name
     */
    private String phaseName;
    
    /**
     * Phase status (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     */
    private String status;
    
    /**
     * Progress percentage for this phase (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * When the phase started
     */
    private LocalDateTime startedAt;
    
    /**
     * When the phase completed (if completed)
     */
    private LocalDateTime completedAt;
    
    /**
     * Duration of the phase (if completed)
     */
    private Long durationMs;
    
    /**
     * Current step within the phase
     */
    private String currentStep;
    
    /**
     * Total steps in this phase
     */
    private Integer totalSteps;
    
    /**
     * Completed steps in this phase
     */
    private Integer completedSteps;
    
    /**
     * Any errors in this phase
     */
    private List<String> phaseErrors;
    
    /**
     * Any warnings in this phase
     */
    private List<String> phaseWarnings;
}

/**
 * Completed activity information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CompletedActivity {
    
    /**
     * Activity name
     */
    private String activityName;
    
    /**
     * Activity description
     */
    private String activityDescription;
    
    /**
     * When the activity started
     */
    private LocalDateTime startedAt;
    
    /**
     * When the activity completed
     */
    private LocalDateTime completedAt;
    
    /**
     * Duration of the activity
     */
    private Long durationMs;
    
    /**
     * Whether the activity was successful
     */
    private boolean wasSuccessful;
    
    /**
     * Result or outcome of the activity
     */
    private String result;
    
    /**
     * Records processed in this activity
     */
    private Long recordsProcessed;
    
    /**
     * Any warnings from this activity
     */
    private List<String> warnings;
}

/**
 * Activity currently in progress
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ActivityInProgress {
    
    /**
     * Activity name
     */
    private String activityName;
    
    /**
     * Activity description
     */
    private String activityDescription;
    
    /**
     * When the activity started
     */
    private LocalDateTime startedAt;
    
    /**
     * Progress percentage for this activity (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * Estimated completion time
     */
    private LocalDateTime estimatedCompletionAt;
    
    /**
     * Current status message
     */
    private String statusMessage;
    
    /**
     * Records processed so far
     */
    private Long recordsProcessed;
    
    /**
     * Total records to process (if known)
     */
    private Long totalRecords;
    
    /**
     * Processing rate (records per second)
     */
    private BigDecimal processingRate;
}

/**
 * Pending activity information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PendingActivity {
    
    /**
     * Activity name
     */
    private String activityName;
    
    /**
     * Activity description
     */
    private String activityDescription;
    
    /**
     * Estimated start time
     */
    private LocalDateTime estimatedStartAt;
    
    /**
     * Estimated duration
     */
    private Long estimatedDurationMs;
    
    /**
     * Dependencies that must complete first
     */
    private List<String> dependencies;
    
    /**
     * Whether this activity requires manual intervention
     */
    private boolean requiresManualIntervention;
    
    /**
     * Priority level
     */
    private String priority;
}

/**
 * Close process metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CloseProcessMetrics {
    
    /**
     * Total number of journal entries processed
     */
    private Long totalJournalEntriesProcessed;
    
    /**
     * Total number of accounts processed
     */
    private Integer totalAccountsProcessed;
    
    /**
     * Total number of transactions processed
     */
    private Long totalTransactionsProcessed;
    
    /**
     * Average processing time per journal entry
     */
    private Long avgProcessingTimePerEntry;
    
    /**
     * Peak memory usage during the process
     */
    private Long peakMemoryUsageMB;
    
    /**
     * Average memory usage
     */
    private Long avgMemoryUsageMB;
    
    /**
     * CPU utilization percentage
     */
    private BigDecimal cpuUtilizationPercent;
    
    /**
     * Database queries executed
     */
    private Long databaseQueriesExecuted;
    
    /**
     * Network I/O operations
     */
    private Long networkIOOperations;
    
    /**
     * Disk I/O operations
     */
    private Long diskIOOperations;
}

/**
 * Process error information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProcessError {
    
    /**
     * Error ID
     */
    private String errorId;
    
    /**
     * Error code
     */
    private String errorCode;
    
    /**
     * Error message
     */
    private String errorMessage;
    
    /**
     * Error severity (CRITICAL, HIGH, MEDIUM, LOW)
     */
    private String severity;
    
    /**
     * Activity where error occurred
     */
    private String sourceActivity;
    
    /**
     * When the error occurred
     */
    private LocalDateTime occurredAt;
    
    /**
     * Whether the error was resolved
     */
    private boolean isResolved;
    
    /**
     * Resolution action taken
     */
    private String resolutionAction;
    
    /**
     * Who resolved the error
     */
    private String resolvedBy;
    
    /**
     * When the error was resolved
     */
    private LocalDateTime resolvedAt;
}

/**
 * Process warning information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProcessWarning {
    
    /**
     * Warning ID
     */
    private String warningId;
    
    /**
     * Warning code
     */
    private String warningCode;
    
    /**
     * Warning message
     */
    private String warningMessage;
    
    /**
     * Warning category
     */
    private String category;
    
    /**
     * Activity where warning occurred
     */
    private String sourceActivity;
    
    /**
     * When the warning occurred
     */
    private LocalDateTime occurredAt;
    
    /**
     * Whether the warning was acknowledged
     */
    private boolean isAcknowledged;
    
    /**
     * Who acknowledged the warning
     */
    private String acknowledgedBy;
    
    /**
     * When the warning was acknowledged
     */
    private LocalDateTime acknowledgedAt;
}

/**
 * Resource utilization information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResourceUtilization {
    
    /**
     * CPU usage percentage
     */
    private BigDecimal cpuUsagePercent;
    
    /**
     * Memory usage in MB
     */
    private Long memoryUsageMB;
    
    /**
     * Available memory in MB
     */
    private Long availableMemoryMB;
    
    /**
     * Database connections in use
     */
    private Integer databaseConnectionsInUse;
    
    /**
     * Available database connections
     */
    private Integer availableDatabaseConnections;
    
    /**
     * Thread count
     */
    private Integer threadCount;
    
    /**
     * Active thread count
     */
    private Integer activeThreadCount;
    
    /**
     * Queue size
     */
    private Integer queueSize;
}

/**
 * Process dependency information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProcessDependency {
    
    /**
     * Dependency name
     */
    private String dependencyName;
    
    /**
     * Dependency description
     */
    private String dependencyDescription;
    
    /**
     * Dependency type
     */
    private String dependencyType;
    
    /**
     * Current status of the dependency
     */
    private String status;
    
    /**
     * Whether the dependency is satisfied
     */
    private boolean isSatisfied;
    
    /**
     * Expected resolution time
     */
    private LocalDateTime expectedResolutionAt;
    
    /**
     * Who is responsible for resolving
     */
    private String responsibleParty;
}

/**
 * Manual intervention required
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ManualIntervention {
    
    /**
     * Intervention ID
     */
    private String interventionId;
    
    /**
     * Intervention description
     */
    private String description;
    
    /**
     * Intervention type
     */
    private String interventionType;
    
    /**
     * Priority level
     */
    private String priority;
    
    /**
     * When intervention was requested
     */
    private LocalDateTime requestedAt;
    
    /**
     * Who should perform the intervention
     */
    private String assignedTo;
    
    /**
     * Current status
     */
    private String status;
    
    /**
     * Instructions for the intervention
     */
    private String instructions;
    
    /**
     * Expected completion time
     */
    private LocalDateTime expectedCompletionAt;
}

/**
 * Process notification information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProcessNotification {
    
    /**
     * Notification ID
     */
    private String notificationId;
    
    /**
     * Notification type
     */
    private String notificationType;
    
    /**
     * Notification message
     */
    private String message;
    
    /**
     * Recipient
     */
    private String recipient;
    
    /**
     * When notification was sent
     */
    private LocalDateTime sentAt;
    
    /**
     * Delivery status
     */
    private String deliveryStatus;
    
    /**
     * Delivery channel (EMAIL, SMS, SYSTEM)
     */
    private String deliveryChannel;
}

/**
 * Process audit entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProcessAuditEntry {
    
    /**
     * Audit entry ID
     */
    private String auditId;
    
    /**
     * Event type
     */
    private String eventType;
    
    /**
     * Event description
     */
    private String eventDescription;
    
    /**
     * When the event occurred
     */
    private LocalDateTime eventTimestamp;
    
    /**
     * User who triggered the event
     */
    private String userId;
    
    /**
     * System or component that logged the event
     */
    private String source;
    
    /**
     * Additional event data
     */
    private String eventData;
}

/**
 * Close process configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CloseProcessConfiguration {
    
    /**
     * Close type configured
     */
    private String closeType;
    
    /**
     * Validation enabled
     */
    private boolean validationEnabled;
    
    /**
     * Generate reports enabled
     */
    private boolean generateReportsEnabled;
    
    /**
     * Create reversing entries enabled
     */
    private boolean createReversingEntriesEnabled;
    
    /**
     * Notification settings
     */
    private Map<String, Boolean> notificationSettings;
    
    /**
     * Timeout settings
     */
    private Map<String, Long> timeoutSettings;
    
    /**
     * Retry settings
     */
    private Map<String, Integer> retrySettings;
}

/**
 * Period close result summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PeriodCloseResultSummary {
    
    /**
     * Whether the close was successful
     */
    private boolean successful;
    
    /**
     * Net income calculated
     */
    private BigDecimal netIncome;
    
    /**
     * Total revenue closed
     */
    private BigDecimal totalRevenueClosed;
    
    /**
     * Total expenses closed
     */
    private BigDecimal totalExpensesClosed;
    
    /**
     * Number of journal entries created
     */
    private Integer journalEntriesCreated;
    
    /**
     * Number of accounts closed
     */
    private Integer accountsClosed;
    
    /**
     * Financial statements generated
     */
    private boolean financialStatementsGenerated;
    
    /**
     * Reports generated
     */
    private List<String> reportsGenerated;
}