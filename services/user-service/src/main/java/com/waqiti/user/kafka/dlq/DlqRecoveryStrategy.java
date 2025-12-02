package com.waqiti.user.kafka.dlq;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines recovery strategies for Dead Letter Queue messages
 * Each strategy determines how the system should respond to failed messages
 */
@Getter
@RequiredArgsConstructor
public enum DlqRecoveryStrategy {

    /**
     * Immediate retry with exponential backoff
     * Use for: Transient failures (network issues, temporary service unavailability)
     */
    RETRY_WITH_BACKOFF("Retry with exponential backoff", true),

    /**
     * Queue for manual review by operations team
     * Use for: Business logic failures requiring human judgment
     */
    MANUAL_REVIEW("Queue for manual review", false),

    /**
     * Alert security team and take immediate protective action
     * Use for: Security-critical events (fraud alerts, suspicious activity)
     */
    SECURITY_ALERT("Alert security team immediately", true),

    /**
     * Compensating transaction - rollback or fix data inconsistency
     * Use for: Financial transactions that partially failed
     */
    COMPENSATE("Execute compensating transaction", true),

    /**
     * Log and continue - event can be safely ignored
     * Use for: Non-critical informational events
     */
    LOG_AND_IGNORE("Log and ignore", false),

    /**
     * Store for batch processing during off-peak hours
     * Use for: Heavy processing that can be deferred
     */
    DEFER_TO_BATCH("Defer to batch processing", false),

    /**
     * Trigger pagerduty incident for immediate engineering response
     * Use for: Critical infrastructure failures
     */
    ESCALATE_TO_ENGINEERING("Escalate to engineering via PagerDuty", true);

    private final String description;
    private final boolean requiresImmediateAction;
}
