package com.waqiti.common.kafka.dlq;

/**
 * Alert severity levels for DLQ processing
 */
public enum AlertSeverity {
    /**
     * Informational - no action required
     */
    INFO,

    /**
     * Warning - investigate when convenient
     */
    WARNING,

    /**
     * Error - action required within hours
     */
    ERROR,

    /**
     * Critical - immediate action required
     */
    CRITICAL
}
