package com.waqiti.wallet.dto;

/**
 * Alert delivery channels for notifications.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-22
 */
public enum AlertChannel {
    /**
     * PagerDuty for critical incidents.
     */
    PAGERDUTY,

    /**
     * Slack for team notifications.
     */
    SLACK,

    /**
     * Email for compliance and audit trail.
     */
    EMAIL,

    /**
     * SMS for urgent customer notifications.
     */
    SMS,

    /**
     * Push notifications for mobile app.
     */
    PUSH,

    /**
     * In-app notifications.
     */
    IN_APP,

    /**
     * Webhook for external integrations.
     */
    WEBHOOK
}
