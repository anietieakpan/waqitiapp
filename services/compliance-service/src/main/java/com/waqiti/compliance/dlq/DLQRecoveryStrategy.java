package com.waqiti.compliance.dlq;

/**
 * Dead Letter Queue Recovery Strategies.
 *
 * Defines how failed messages should be handled based on failure type,
 * message priority, and business criticality.
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
public enum DLQRecoveryStrategy {
    /**
     * Immediate retry with escalation to compliance team.
     * Used for critical compliance messages (SAR/CTR filing, transaction blocking).
     *
     * Behavior:
     * - Retry immediately after 1 second delay
     * - Escalate to critical queue
     * - Send PagerDuty alert
     * - Add to manual review queue
     */
    IMMEDIATE_RETRY_WITH_ESCALATION,

    /**
     * Retry with exponential backoff.
     * Used for transient failures (network timeout, service unavailable).
     *
     * Behavior:
     * - Initial delay: 1 second
     * - Backoff multiplier: 2.0
     * - Max delay: 5 minutes
     * - Max retries: 5
     * - If all retries fail, escalate to manual review
     */
    EXPONENTIAL_BACKOFF_RETRY,

    /**
     * Immediate manual review required.
     * Used for permanent failures (schema mismatch, deserialization error).
     *
     * Behavior:
     * - Add to manual review queue immediately
     * - Send notification to compliance team
     * - Create investigation case
     * - No automatic retry
     */
    MANUAL_REVIEW_REQUIRED,

    /**
     * Discard message (use with extreme caution).
     * Only for non-critical, unrecoverable messages.
     *
     * Behavior:
     * - Mark as discarded
     * - Log to audit trail
     * - No retry
     * - No notification (unless configured)
     */
    DISCARD
}
