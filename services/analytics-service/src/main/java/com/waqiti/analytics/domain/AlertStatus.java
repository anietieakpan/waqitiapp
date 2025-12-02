package com.waqiti.analytics.domain;

import lombok.Getter;

/**
 * Alert Status Enum
 *
 * Represents the complete lifecycle of an alert from creation to resolution.
 * Used for alert state management, workflow automation, and SLA tracking.
 *
 * Status Transitions:
 * OPEN → ACKNOWLEDGED → IN_PROGRESS → (RESOLVED | ESCALATED | FALSE_POSITIVE | CLOSED)
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Getter
public enum AlertStatus {

    /**
     * Alert has been created but not yet acknowledged
     * SLA: Must be acknowledged within 15 minutes
     */
    OPEN("Open", "Alert created, awaiting acknowledgment", 1, true),

    /**
     * Alert has been acknowledged by on-call engineer
     * SLA: Must start investigation within 30 minutes
     */
    ACKNOWLEDGED("Acknowledged", "Alert acknowledged, investigation pending", 2, true),

    /**
     * Alert is being actively investigated
     * SLA: Must provide update every 2 hours
     */
    IN_PROGRESS("In Progress", "Active investigation underway", 3, true),

    /**
     * Alert has been successfully resolved
     * Final state - no further action required
     */
    RESOLVED("Resolved", "Alert resolved successfully", 4, false),

    /**
     * Alert has been escalated to higher tier support
     * SLA: Tier 2 must respond within 1 hour
     */
    ESCALATED("Escalated", "Alert escalated to higher tier", 5, true),

    /**
     * Alert was determined to be a false positive
     * Final state - improves detection rules
     */
    FALSE_POSITIVE("False Positive", "Alert was false positive", 6, false),

    /**
     * Alert was closed without resolution
     * Final state - requires explanation
     */
    CLOSED("Closed", "Alert closed without resolution", 7, false),

    /**
     * Alert requires manual review before processing
     * Typically for high-severity or ambiguous alerts
     */
    PENDING_REVIEW("Pending Review", "Awaiting manual review", 8, true);

    private final String displayName;
    private final String description;
    private final int priority;
    private final boolean requiresAction;

    AlertStatus(String displayName, String description, int priority, boolean requiresAction) {
        this.displayName = displayName;
        this.description = description;
        this.priority = priority;
        this.requiresAction = requiresAction;
    }

    /**
     * Check if this status is a final state (terminal)
     */
    public boolean isFinal() {
        return this == RESOLVED || this == FALSE_POSITIVE || this == CLOSED;
    }

    /**
     * Check if this status is actionable (requires human intervention)
     */
    public boolean isActionable() {
        return requiresAction;
    }

    /**
     * Get next valid statuses from current status
     */
    public AlertStatus[] getValidNextStatuses() {
        return switch (this) {
            case OPEN -> new AlertStatus[]{ACKNOWLEDGED, FALSE_POSITIVE, CLOSED};
            case ACKNOWLEDGED -> new AlertStatus[]{IN_PROGRESS, ESCALATED, FALSE_POSITIVE};
            case IN_PROGRESS -> new AlertStatus[]{RESOLVED, ESCALATED, PENDING_REVIEW};
            case ESCALATED -> new AlertStatus[]{RESOLVED, IN_PROGRESS, CLOSED};
            case PENDING_REVIEW -> new AlertStatus[]{IN_PROGRESS, FALSE_POSITIVE, CLOSED};
            default -> new AlertStatus[]{};  // Final states have no transitions
        };
    }

    /**
     * Check if transition to target status is valid
     */
    public boolean canTransitionTo(AlertStatus target) {
        for (AlertStatus validStatus : getValidNextStatuses()) {
            if (validStatus == target) {
                return true;
            }
        }
        return false;
    }
}
