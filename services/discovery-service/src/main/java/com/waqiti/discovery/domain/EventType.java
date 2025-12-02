package com.waqiti.discovery.domain;

/**
 * Service event types
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum EventType {
    // Registration Events
    SERVICE_REGISTERED,
    SERVICE_DEREGISTERED,
    INSTANCE_REGISTERED,
    INSTANCE_DEREGISTERED,

    // Health Events
    HEALTH_CHECK_PASSED,
    HEALTH_CHECK_FAILED,
    SERVICE_HEALTHY,
    SERVICE_UNHEALTHY,
    SERVICE_DEGRADED,

    // Status Events
    STATUS_CHANGED,
    INSTANCE_STATUS_CHANGED,
    SERVICE_ENABLED,
    SERVICE_DISABLED,

    // Circuit Breaker Events
    CIRCUIT_BREAKER_OPENED,
    CIRCUIT_BREAKER_CLOSED,
    CIRCUIT_BREAKER_HALF_OPEN,

    // Configuration Events
    CONFIGURATION_UPDATED,
    CONFIGURATION_CREATED,
    CONFIGURATION_DELETED,

    // Dependency Events
    DEPENDENCY_ADDED,
    DEPENDENCY_REMOVED,
    DEPENDENCY_FAILED,
    DEPENDENCY_RECOVERED,

    // Metrics Events
    METRICS_THRESHOLD_EXCEEDED,
    PERFORMANCE_DEGRADATION,

    // System Events
    REGISTRY_SYNC,
    REGISTRY_BACKUP,
    REGISTRY_RESTORE;

    /**
     * Check if event is critical
     *
     * @return true if event requires immediate attention
     */
    public boolean isCritical() {
        return this == SERVICE_UNHEALTHY
            || this == CIRCUIT_BREAKER_OPENED
            || this == DEPENDENCY_FAILED
            || this == HEALTH_CHECK_FAILED;
    }
}
