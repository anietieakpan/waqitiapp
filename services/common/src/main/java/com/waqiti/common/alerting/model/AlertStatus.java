package com.waqiti.common.alerting.model;

/**
 * Alert lifecycle status
 */
public enum AlertStatus {

    /**
     * Alert is open and needs attention
     */
    OPEN,

    /**
     * Alert has been acknowledged by on-call engineer
     * (stops paging but keeps incident open)
     */
    ACKNOWLEDGED,

    /**
     * Alert has been resolved
     */
    RESOLVED,

    /**
     * Alert was suppressed (maintenance mode, etc.)
     */
    SUPPRESSED,

    /**
     * Alert was closed automatically (timeout, etc.)
     */
    CLOSED,

    /**
     * Alert is being investigated
     */
    IN_PROGRESS,

    /**
     * Alert escalated to higher tier support
     */
    ESCALATED
}
