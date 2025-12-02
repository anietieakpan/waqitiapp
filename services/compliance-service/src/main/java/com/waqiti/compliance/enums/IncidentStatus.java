package com.waqiti.compliance.enums;

/**
 * Incident Status Enumeration
 *
 * Defines lifecycle states for compliance incidents with
 * validated state transitions for audit compliance.
 *
 * Compliance: SOX 404 (change tracking), PCI DSS 10.x
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
public enum IncidentStatus {
    /**
     * OPEN - Incident created, awaiting investigation
     * Initial state
     */
    OPEN,

    /**
     * IN_PROGRESS - Incident under active investigation
     * Transition from: OPEN, ESCALATED
     */
    IN_PROGRESS,

    /**
     * ESCALATED - Incident escalated to higher authority
     * Transition from: OPEN, IN_PROGRESS
     */
    ESCALATED,

    /**
     * RESOLVED - Incident resolved, awaiting closure
     * Transition from: IN_PROGRESS, ESCALATED
     */
    RESOLVED,

    /**
     * CLOSED - Incident closed, no further action
     * Terminal state
     * Transition from: RESOLVED
     */
    CLOSED;

    /**
     * Check if status is terminal (no further transitions allowed)
     *
     * @return true if terminal status
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    /**
     * Check if status is active (incident still open)
     *
     * @return true if active
     */
    public boolean isActive() {
        return this != RESOLVED && this != CLOSED;
    }

    /**
     * Check if status allows assignment changes
     *
     * @return true if assignment can be changed
     */
    public boolean allowsReassignment() {
        return this == OPEN || this == IN_PROGRESS || this == ESCALATED;
    }

    /**
     * Check if status requires active investigation
     *
     * @return true if investigation required
     */
    public boolean requiresInvestigation() {
        return this == IN_PROGRESS || this == ESCALATED;
    }

    /**
     * Check if transition to target status is valid
     *
     * @param targetStatus the status to transition to
     * @return true if transition is valid
     */
    public boolean canTransitionTo(IncidentStatus targetStatus) {
        // Cannot transition from terminal state
        if (this.isTerminal()) {
            return false;
        }

        // Cannot transition to same state
        if (this == targetStatus) {
            return false;
        }

        switch (this) {
            case OPEN:
                // OPEN can transition to IN_PROGRESS or ESCALATED
                return targetStatus == IN_PROGRESS || targetStatus == ESCALATED;

            case IN_PROGRESS:
                // IN_PROGRESS can transition to ESCALATED, RESOLVED
                return targetStatus == ESCALATED || targetStatus == RESOLVED;

            case ESCALATED:
                // ESCALATED can transition to IN_PROGRESS or RESOLVED
                return targetStatus == IN_PROGRESS || targetStatus == RESOLVED;

            case RESOLVED:
                // RESOLVED can only transition to CLOSED
                return targetStatus == CLOSED;

            case CLOSED:
                // CLOSED is terminal - no transitions allowed
                return false;

            default:
                return false;
        }
    }

    /**
     * Get allowed next statuses from current status
     *
     * @return array of allowed next statuses
     */
    public IncidentStatus[] getAllowedTransitions() {
        switch (this) {
            case OPEN:
                return new IncidentStatus[]{IN_PROGRESS, ESCALATED};
            case IN_PROGRESS:
                return new IncidentStatus[]{ESCALATED, RESOLVED};
            case ESCALATED:
                return new IncidentStatus[]{IN_PROGRESS, RESOLVED};
            case RESOLVED:
                return new IncidentStatus[]{CLOSED};
            case CLOSED:
            default:
                return new IncidentStatus[]{};
        }
    }

    /**
     * Check if status requires resolution notes
     *
     * @return true if resolution notes required
     */
    public boolean requiresResolutionNotes() {
        return this == RESOLVED || this == CLOSED;
    }

    /**
     * Check if status requires escalation reason
     *
     * @return true if escalation reason required
     */
    public boolean requiresEscalationReason() {
        return this == ESCALATED;
    }
}
