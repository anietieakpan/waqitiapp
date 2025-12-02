package com.waqiti.common.audit;

/**
 * Audit event severity levels for compliance and monitoring.
 *
 * Used to classify the severity of security and audit events to prioritize
 * monitoring, alerting, and compliance reporting.
 *
 * Levels:
 * - LOW: Informational events (normal operations)
 * - MEDIUM: Important events requiring logging (access grants, configuration changes)
 * - HIGH: Critical events requiring immediate attention (access denials, security violations)
 * - CRITICAL: Severe events requiring urgent response (system breaches, data exfiltration)
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
public enum AuditLevel {
    /**
     * Low severity - informational events.
     * Examples: successful login, routine data access
     */
    LOW,

    /**
     * Medium severity - important events that should be logged.
     * Examples: configuration changes, permission grants, user registration
     */
    MEDIUM,

    /**
     * High severity - critical events requiring attention.
     * Examples: failed authentication, access denials, unusual activity
     */
    HIGH,

    /**
     * Critical severity - urgent events requiring immediate response.
     * Examples: security breaches, repeated attack attempts, system compromise
     */
    CRITICAL
}
