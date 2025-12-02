package com.waqiti.card.enums;

/**
 * Settlement status enumeration
 * Represents the state of transaction settlement
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum SettlementStatus {
    /**
     * Settlement pending
     */
    PENDING,

    /**
     * Settlement in progress
     */
    PROCESSING,

    /**
     * Settlement completed successfully
     */
    SETTLED,

    /**
     * Settlement failed
     */
    FAILED,

    /**
     * Settlement reversed
     */
    REVERSED,

    /**
     * Awaiting reconciliation
     */
    AWAITING_RECONCILIATION,

    /**
     * Reconciled successfully
     */
    RECONCILED,

    /**
     * Reconciliation discrepancy found
     */
    DISCREPANCY,

    /**
     * Settlement on hold
     */
    ON_HOLD
}
