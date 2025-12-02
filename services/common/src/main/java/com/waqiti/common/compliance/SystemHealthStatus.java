package com.waqiti.common.compliance;

/**
 * System health status enumeration
 *
 * Represents the operational health status of compliance systems and services.
 * Used for monitoring, alerting, and operational dashboards.
 */
public enum SystemHealthStatus {

    /**
     * System is operating normally - all checks passing
     */
    HEALTHY("Healthy", "System is operating normally", 0, false, false),

    /**
     * System is operational but with degraded performance or partial failures
     */
    DEGRADED("Degraded", "System is operational with reduced performance or partial service", 1, true, false),

    /**
     * System is experiencing significant issues but core functionality is available
     */
    IMPAIRED("Impaired", "System has significant issues affecting functionality", 2, true, true),

    /**
     * System is down or unavailable
     */
    DOWN("Down", "System is unavailable or not responding", 3, true, true),

    /**
     * System status cannot be determined
     */
    UNKNOWN("Unknown", "System health status cannot be determined", 2, true, false),

    /**
     * System is undergoing planned maintenance
     */
    MAINTENANCE("Maintenance", "System is under planned maintenance", 1, false, false),

    /**
     * System is starting up or initializing
     */
    STARTING("Starting", "System is initializing", 0, false, false),

    /**
     * System is shutting down gracefully
     */
    SHUTTING_DOWN("Shutting Down", "System is shutting down", 1, true, false),

    /**
     * System is recovering from a failure
     */
    RECOVERING("Recovering", "System is recovering from a failure or degradation", 1, true, false),

    /**
     * System is in fail-safe mode with limited functionality
     */
    FAIL_SAFE("Fail-Safe", "System is operating in fail-safe mode with limited functionality", 2, true, true);

    private final String displayName;
    private final String description;
    private final int severityLevel;
    private final boolean requiresAlert;
    private final boolean requiresEscalation;

    SystemHealthStatus(String displayName, String description, int severityLevel,
                      boolean requiresAlert, boolean requiresEscalation) {
        this.displayName = displayName;
        this.description = description;
        this.severityLevel = severityLevel;
        this.requiresAlert = requiresAlert;
        this.requiresEscalation = requiresEscalation;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getSeverityLevel() { return severityLevel; }
    public boolean requiresAlert() { return requiresAlert; }
    public boolean requiresEscalation() { return requiresEscalation; }

    /**
     * Check if the system is operational (can serve requests)
     */
    public boolean isOperational() {
        return this == HEALTHY ||
               this == DEGRADED ||
               this == MAINTENANCE ||
               this == STARTING ||
               this == RECOVERING;
    }

    /**
     * Check if the system is in a critical state
     */
    public boolean isCritical() {
        return this == DOWN || this == IMPAIRED || this == FAIL_SAFE;
    }

    /**
     * Check if the status represents a transitional state
     */
    public boolean isTransitional() {
        return this == STARTING || this == SHUTTING_DOWN || this == RECOVERING;
    }

    /**
     * Check if automated failover should be triggered
     */
    public boolean shouldTriggerFailover() {
        return this == DOWN || this == FAIL_SAFE;
    }

    /**
     * Get the health score (0-100, higher is better)
     */
    public int getHealthScore() {
        switch (this) {
            case HEALTHY: return 100;
            case MAINTENANCE: return 90;
            case STARTING: return 75;
            case DEGRADED: return 60;
            case RECOVERING: return 50;
            case IMPAIRED: return 30;
            case UNKNOWN: return 25;
            case SHUTTING_DOWN: return 20;
            case FAIL_SAFE: return 10;
            case DOWN: return 0;
            default: return 0;
        }
    }

    /**
     * Get CSS class for status styling
     */
    public String getCssClass() {
        switch (this) {
            case HEALTHY: return "status-success";
            case DEGRADED:
            case MAINTENANCE: return "status-warning";
            case IMPAIRED:
            case UNKNOWN: return "status-danger";
            case DOWN:
            case FAIL_SAFE: return "status-critical";
            case STARTING:
            case RECOVERING: return "status-info";
            case SHUTTING_DOWN: return "status-secondary";
            default: return "status-default";
        }
    }

    /**
     * Get icon class for status display
     */
    public String getIconClass() {
        switch (this) {
            case HEALTHY: return "fa-check-circle text-success";
            case DEGRADED: return "fa-exclamation-triangle text-warning";
            case IMPAIRED: return "fa-exclamation-circle text-danger";
            case DOWN: return "fa-times-circle text-danger";
            case UNKNOWN: return "fa-question-circle text-secondary";
            case MAINTENANCE: return "fa-wrench text-info";
            case STARTING: return "fa-spinner fa-spin text-info";
            case SHUTTING_DOWN: return "fa-power-off text-secondary";
            case RECOVERING: return "fa-sync fa-spin text-warning";
            case FAIL_SAFE: return "fa-shield-alt text-danger";
            default: return "fa-circle";
        }
    }

    /**
     * Get HTTP status code representation
     */
    public int getHttpStatusCode() {
        switch (this) {
            case HEALTHY: return 200;
            case DEGRADED:
            case IMPAIRED: return 503;
            case DOWN: return 503;
            case MAINTENANCE: return 503;
            case UNKNOWN: return 500;
            case STARTING:
            case RECOVERING: return 503;
            case SHUTTING_DOWN: return 503;
            case FAIL_SAFE: return 503;
            default: return 500;
        }
    }

    /**
     * Determine the worst status from a collection
     */
    public static SystemHealthStatus worstStatus(SystemHealthStatus... statuses) {
        SystemHealthStatus worst = HEALTHY;
        int worstSeverity = -1;

        for (SystemHealthStatus status : statuses) {
            if (status != null && status.severityLevel > worstSeverity) {
                worst = status;
                worstSeverity = status.severityLevel;
            }
        }

        return worst;
    }

    /**
     * Convert from Spring Boot Health status string
     */
    public static SystemHealthStatus fromSpringHealthStatus(String status) {
        if (status == null) return UNKNOWN;

        switch (status.toUpperCase()) {
            case "UP": return HEALTHY;
            case "DOWN": return DOWN;
            case "OUT_OF_SERVICE": return MAINTENANCE;
            case "UNKNOWN": return UNKNOWN;
            default: return DEGRADED;
        }
    }

    /**
     * Convert to Spring Boot Health status string
     */
    public String toSpringHealthStatus() {
        switch (this) {
            case HEALTHY:
            case DEGRADED:
            case RECOVERING: return "UP";
            case DOWN:
            case FAIL_SAFE: return "DOWN";
            case MAINTENANCE:
            case SHUTTING_DOWN: return "OUT_OF_SERVICE";
            default: return "UNKNOWN";
        }
    }
}
