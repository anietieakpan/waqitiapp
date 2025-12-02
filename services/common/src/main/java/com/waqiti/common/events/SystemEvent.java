package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * System-level event for infrastructure, health, performance, and operational monitoring.
 * Captures system state changes, health checks, deployments, scaling, and operational events.
 * Integrates with monitoring, alerting, and incident management systems.
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"eventId"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemEvent implements DomainEvent {
    
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "System.Event";
    
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    @Builder.Default
    private String topic = "system-events";
    
    private String aggregateId; // System component ID
    
    @Builder.Default
    private String aggregateType = "System";
    
    private Long version;
    
    private String correlationId;
    
    private String userId; // System user or automation ID
    
    @Builder.Default
    private String sourceService = System.getProperty("spring.application.name", "system");
    
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    // System identification
    private String componentId;
    private String componentName;
    private ComponentType componentType;
    private String instanceId;
    private String hostname;
    private String ipAddress;
    private String datacenter;
    private String region;
    private String availabilityZone;
    private String cluster;
    
    // Event categorization
    private SystemEventType systemEventType;
    private EventCategory category;
    private SeverityLevel severity;
    private String eventDescription;
    private String eventCode;
    
    // Health and status
    private HealthStatus previousHealth;
    private HealthStatus currentHealth;
    private ServiceStatus previousStatus;
    private ServiceStatus currentStatus;
    private Long uptimeSeconds;
    private Instant lastHealthCheckTime;
    private Map<String, HealthCheck> healthChecks;
    
    // Performance metrics
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private Long memoryTotal;
    private Long memoryUsed;
    private Long diskTotal;
    private Long diskUsed;
    private Double networkInMbps;
    private Double networkOutMbps;
    private Long activeConnections;
    private Long requestsPerSecond;
    private Double averageResponseTimeMs;
    private Double p99ResponseTimeMs;
    
    // Resource utilization
    private Integer threadCount;
    private Integer activeThreads;
    private Long heapMemoryUsed;
    private Long heapMemoryMax;
    private Integer gcCount;
    private Long gcTimeMs;
    private Map<String, Double> customMetrics;
    
    // Scaling and capacity
    private Integer currentInstances;
    private Integer targetInstances;
    private Integer minInstances;
    private Integer maxInstances;
    private ScalingTrigger scalingTrigger;
    private Double scalingThreshold;
    private String scalingPolicy;
    
    // Deployment and versioning
    private String applicationVersion;
    private String previousVersion;
    private String deploymentId;
    private DeploymentType deploymentType;
    private String deploymentStrategy;
    private Instant deploymentStartTime;
    private Instant deploymentEndTime;
    private DeploymentStatus deploymentStatus;
    private String gitCommitHash;
    private String buildNumber;
    
    // Circuit breaker and resilience
    private String circuitBreakerName;
    private CircuitBreakerState circuitBreakerState;
    private Integer failureCount;
    private Double failureRate;
    private Long circuitOpenDurationMs;
    private Instant lastFailureTime;
    
    // Database and connections
    private String databaseHost;
    private Integer connectionPoolSize;
    private Integer activeDbConnections;
    private Integer idleDbConnections;
    private Long slowQueryCount;
    private Double averageQueryTimeMs;
    private Long deadlockCount;
    
    // Cache performance
    private String cacheType;
    private Long cacheHits;
    private Long cacheMisses;
    private Double cacheHitRate;
    private Long cacheEvictions;
    private Long cacheSizeBytes;
    
    // Message queue metrics
    private String queueName;
    private Long messagesInQueue;
    private Long messagesProcessed;
    private Long messagesFailed;
    private Double messageProcessingRatePerSec;
    private Long queueDepth;
    private Long consumerLag;
    
    // API Gateway metrics
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Map<Integer, Long> responseCodeCounts;
    private Double apiErrorRate;  // Renamed to avoid conflict with error tracking errorRate
    private Long rateLimitExceeded;
    
    // Security events
    private SecurityEventType securityEventType;
    private String securityThreatLevel;
    private Long unauthorizedAttempts;
    private String attackVector;
    private String mitigationAction;
    private boolean securityIncident;
    
    // Backup and recovery
    private String backupId;
    private BackupType backupType;
    private BackupStatus backupStatus;
    private Long backupSizeBytes;
    private Long backupDurationMs;
    private Instant lastSuccessfulBackup;
    
    // Maintenance and operations
    private MaintenanceType maintenanceType;
    private Instant maintenanceStartTime;
    private Instant maintenanceEndTime;
    private String maintenanceReason;
    private boolean plannedMaintenance;
    
    // Error and exception details
    private String errorType;
    private String errorMessage;
    private String stackTrace;
    private String rootCause;
    private Integer errorCount;
    private Double errorRate;
    
    // Alerting and incident management
    private String alertId;
    private String incidentId;
    private AlertPriority alertPriority;
    private String alertRule;
    private boolean alertTriggered;
    private boolean incidentCreated;
    private String runbookUrl;
    private String[] notifiedTeams;
    
    // Compliance and audit
    private boolean complianceEvent;
    private String complianceType;
    private String auditAction;
    private Map<String, String> auditDetails;
    
    /**
     * Types of system events
     */
    public enum SystemEventType {
        // Lifecycle events
        SERVICE_STARTED,
        SERVICE_STOPPED,
        SERVICE_RESTARTED,
        SERVICE_CRASHED,
        
        // Health events
        HEALTH_CHECK_PASSED,
        HEALTH_CHECK_FAILED,
        HEALTH_DEGRADED,
        HEALTH_RECOVERED,
        
        // Scaling events
        SCALE_UP,
        SCALE_DOWN,
        AUTO_SCALING_TRIGGERED,
        SCALING_FAILED,
        
        // Deployment events
        DEPLOYMENT_STARTED,
        DEPLOYMENT_COMPLETED,
        DEPLOYMENT_FAILED,
        ROLLBACK_INITIATED,
        ROLLBACK_COMPLETED,
        
        // Performance events
        HIGH_CPU_USAGE,
        HIGH_MEMORY_USAGE,
        HIGH_DISK_USAGE,
        SLOW_RESPONSE,
        THROUGHPUT_DEGRADED,
        
        // Circuit breaker events
        CIRCUIT_OPENED,
        CIRCUIT_CLOSED,
        CIRCUIT_HALF_OPEN,
        
        // Database events
        CONNECTION_POOL_EXHAUSTED,
        SLOW_QUERY_DETECTED,
        DEADLOCK_DETECTED,
        DATABASE_CONNECTION_LOST,
        DATABASE_CONNECTION_RESTORED,
        
        // Cache events
        CACHE_MISS_RATE_HIGH,
        CACHE_EVICTION_HIGH,
        CACHE_FULL,
        
        // Queue events
        QUEUE_DEPTH_HIGH,
        CONSUMER_LAG_HIGH,
        MESSAGE_PROCESSING_FAILED,
        
        // Security events
        SECURITY_THREAT_DETECTED,
        UNAUTHORIZED_ACCESS_ATTEMPT,
        DDOS_ATTACK_DETECTED,
        CERTIFICATE_EXPIRING,
        
        // Backup events
        BACKUP_STARTED,
        BACKUP_COMPLETED,
        BACKUP_FAILED,
        
        // Maintenance events
        MAINTENANCE_STARTED,
        MAINTENANCE_COMPLETED,
        EMERGENCY_MAINTENANCE
    }
    
    /**
     * Event categories for filtering and routing
     */
    public enum EventCategory {
        INFRASTRUCTURE,
        APPLICATION,
        SECURITY,
        PERFORMANCE,
        AVAILABILITY,
        DEPLOYMENT,
        CONFIGURATION,
        COMPLIANCE,
        BACKUP,
        MAINTENANCE
    }
    
    /**
     * Severity levels for prioritization
     */
    public enum SeverityLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL,
        EMERGENCY
    }
    
    /**
     * Component types in the system
     */
    public enum ComponentType {
        MICROSERVICE,
        DATABASE,
        CACHE,
        MESSAGE_QUEUE,
        API_GATEWAY,
        LOAD_BALANCER,
        CONTAINER,
        KUBERNETES_POD,
        NETWORK,
        STORAGE
    }
    
    /**
     * Health status enumeration
     */
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
    
    /**
     * Service operational status
     */
    public enum ServiceStatus {
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        FAILED,
        MAINTENANCE
    }
    
    /**
     * Scaling triggers
     */
    public enum ScalingTrigger {
        CPU,
        MEMORY,
        REQUEST_RATE,
        RESPONSE_TIME,
        QUEUE_DEPTH,
        CUSTOM_METRIC,
        SCHEDULED,
        MANUAL
    }
    
    /**
     * Deployment types
     */
    public enum DeploymentType {
        ROLLING,
        BLUE_GREEN,
        CANARY,
        RECREATE,
        A_B_TESTING,
        FEATURE_FLAG,
        HOTFIX,
        ROLLBACK
    }
    
    /**
     * Deployment status
     */
    public enum DeploymentStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLED_BACK,
        PAUSED
    }
    
    /**
     * Circuit breaker states
     */
    public enum CircuitBreakerState {
        CLOSED,
        OPEN,
        HALF_OPEN,
        FORCED_OPEN,
        DISABLED
    }
    
    /**
     * Security event types
     */
    public enum SecurityEventType {
        INTRUSION_ATTEMPT,
        BRUTE_FORCE,
        SQL_INJECTION,
        XSS_ATTEMPT,
        DDOS,
        UNAUTHORIZED_ACCESS,
        CERTIFICATE_ISSUE,
        VULNERABILITY_DETECTED
    }
    
    /**
     * Backup types
     */
    public enum BackupType {
        FULL,
        INCREMENTAL,
        DIFFERENTIAL,
        SNAPSHOT,
        REPLICATION
    }
    
    /**
     * Backup status
     */
    public enum BackupStatus {
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CORRUPTED,
        VERIFIED
    }
    
    /**
     * Maintenance types
     */
    public enum MaintenanceType {
        SCHEDULED,
        EMERGENCY,
        PATCH,
        UPGRADE,
        MIGRATION,
        CLEANUP,
        OPTIMIZATION
    }
    
    /**
     * Alert priority levels
     */
    public enum AlertPriority {
        P1_CRITICAL,
        P2_HIGH,
        P3_MEDIUM,
        P4_LOW,
        P5_INFO
    }
    
    /**
     * Health check details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthCheck {
        private String name;
        private HealthStatus status;
        private String message;
        private Long responseTimeMs;
        private Instant lastChecked;
        private Map<String, Object> details;
    }
    
    /**
     * Factory method for service startup event
     */
    public static SystemEvent serviceStarted(String serviceName, String instanceId, String version) {
        return SystemEvent.builder()
                .eventType("System.ServiceStarted")
                .systemEventType(SystemEventType.SERVICE_STARTED)
                .componentName(serviceName)
                .instanceId(instanceId)
                .aggregateId(instanceId)
                .applicationVersion(version)
                .currentStatus(ServiceStatus.RUNNING)
                .currentHealth(HealthStatus.HEALTHY)
                .severity(SeverityLevel.INFO)
                .category(EventCategory.INFRASTRUCTURE)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for health degradation event
     */
    public static SystemEvent healthDegraded(String componentId, String reason, Map<String, HealthCheck> checks) {
        return SystemEvent.builder()
                .eventType("System.HealthDegraded")
                .systemEventType(SystemEventType.HEALTH_DEGRADED)
                .componentId(componentId)
                .aggregateId(componentId)
                .currentHealth(HealthStatus.DEGRADED)
                .healthChecks(checks)
                .eventDescription(reason)
                .severity(SeverityLevel.WARNING)
                .category(EventCategory.AVAILABILITY)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for scaling event
     */
    public static SystemEvent scaled(String serviceName, int from, int to, ScalingTrigger trigger) {
        return SystemEvent.builder()
                .eventType("System.Scaled")
                .systemEventType(SystemEventType.AUTO_SCALING_TRIGGERED)
                .componentName(serviceName)
                .aggregateId(serviceName)
                .currentInstances(to)
                .targetInstances(to)
                .scalingTrigger(trigger)
                .eventDescription(String.format("Scaled from %d to %d instances", from, to))
                .severity(SeverityLevel.INFO)
                .category(EventCategory.INFRASTRUCTURE)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Check if this is a critical system event
     */
    public boolean isCritical() {
        return severity == SeverityLevel.CRITICAL ||
               severity == SeverityLevel.EMERGENCY ||
               currentHealth == HealthStatus.UNHEALTHY ||
               currentStatus == ServiceStatus.FAILED;
    }
    
    /**
     * Check if immediate action required
     */
    public boolean requiresImmediateAction() {
        return isCritical() ||
               securityIncident ||
               (circuitBreakerState == CircuitBreakerState.OPEN) ||
               (deploymentStatus == DeploymentStatus.FAILED);
    }
    
    /**
     * Get operational impact level
     */
    public String getImpactLevel() {
        if (isCritical()) return "SEVERE";
        if (currentHealth == HealthStatus.DEGRADED) return "MODERATE";
        if (severity == SeverityLevel.WARNING) return "LOW";
        return "NONE";
    }
    
    /**
     * Check if this represents a recovery event
     */
    public boolean isRecovery() {
        return (previousHealth == HealthStatus.UNHEALTHY && currentHealth == HealthStatus.HEALTHY) ||
               (previousStatus == ServiceStatus.FAILED && currentStatus == ServiceStatus.RUNNING) ||
               systemEventType == SystemEventType.HEALTH_RECOVERED;
    }
    
    @Override
    public boolean isValid() {
        return eventId != null &&
               systemEventType != null &&
               timestamp != null &&
               (componentId != null || componentName != null);
    }
    
    @Override
    public Integer getPriority() {
        switch (severity) {
            case EMERGENCY: return 10;
            case CRITICAL: return 9;
            case ERROR: return 7;
            case WARNING: return 5;
            case INFO: return 3;
            case DEBUG: return 1;
            default: return 5;
        }
    }
    
    @Override
    public boolean isAsync() {
        // Critical events should be processed synchronously
        return !isCritical();
    }
    
    @Override
    public Long getTtlSeconds() {
        // System events have varying retention based on severity
        switch (severity) {
            case EMERGENCY:
            case CRITICAL: return 31536000L; // 1 year
            case ERROR: return 2592000L; // 30 days
            case WARNING: return 604800L; // 7 days
            case INFO: return 86400L; // 1 day
            case DEBUG: return 3600L; // 1 hour
            default: return 86400L;
        }
    }
    
    @Override
    public String getAggregateName() {
        return "System Component";
    }
}