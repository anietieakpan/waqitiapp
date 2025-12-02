package com.waqiti.compliance.dlq;

/**
 * Dead Letter Queue Message Priority Levels.
 *
 * Determines the recovery strategy and notification channels for DLQ messages.
 *
 * CRITICAL: SAR/CTR filing, transaction blocking, sanctions screening
 *           - Immediate PagerDuty alert
 *           - Immediate email to compliance team
 *           - Slack critical channel notification
 *           - Manual review required
 *
 * HIGH: AML alerts, KYC verification failures, regulatory reporting
 *       - Email to compliance team
 *       - Slack alert channel notification
 *       - Exponential backoff retry
 *
 * MEDIUM: Audit events, standard notifications, non-critical events
 *         - Slack notification channel
 *         - Standard retry logic
 *
 * LOW: Informational events, metrics, non-essential operations
 *      - Logged only
 *      - Background retry
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
public enum DLQPriority {
    /**
     * Critical regulatory/compliance messages.
     * Failure could result in regulatory violations or financial loss.
     */
    CRITICAL,

    /**
     * High priority messages requiring prompt attention.
     * Failure could impact compliance posture or customer service.
     */
    HIGH,

    /**
     * Medium priority messages.
     * Failure should be resolved but does not require immediate action.
     */
    MEDIUM,

    /**
     * Low priority messages.
     * Failure has minimal impact and can be resolved in normal course of business.
     */
    LOW
}
