package com.waqiti.investment.broker.enums;

/**
 * Order Execution Status from Broker
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
public enum ExecutionStatus {
    /**
     * Order accepted by broker
     */
    ACCEPTED,

    /**
     * Order pending execution
     */
    PENDING,

    /**
     * Order partially filled
     */
    PARTIALLY_FILLED,

    /**
     * Order completely filled
     */
    FILLED,

    /**
     * Order cancelled
     */
    CANCELLED,

    /**
     * Order rejected by broker
     */
    REJECTED,

    /**
     * Order expired (time-in-force limit reached)
     */
    EXPIRED,

    /**
     * Execution failed
     */
    FAILED
}
