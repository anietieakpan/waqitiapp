package com.waqiti.card.enums;

/**
 * Dispute status enumeration
 * Represents the current state of a transaction dispute
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum DisputeStatus {
    /**
     * Dispute has been opened
     */
    OPEN,

    /**
     * Dispute is under investigation
     */
    INVESTIGATING,

    /**
     * Awaiting merchant response
     */
    AWAITING_MERCHANT_RESPONSE,

    /**
     * Merchant has responded
     */
    MERCHANT_RESPONDED,

    /**
     * Awaiting cardholder response
     */
    AWAITING_CARDHOLDER_RESPONSE,

    /**
     * Escalated to arbitration
     */
    ARBITRATION,

    /**
     * Resolved in favor of cardholder
     */
    RESOLVED_CARDHOLDER_FAVOR,

    /**
     * Resolved in favor of merchant
     */
    RESOLVED_MERCHANT_FAVOR,

    /**
     * Dispute withdrawn by cardholder
     */
    WITHDRAWN,

    /**
     * Dispute closed
     */
    CLOSED,

    /**
     * Chargeback issued
     */
    CHARGEBACK_ISSUED,

    /**
     * Representment received from merchant
     */
    REPRESENTMENT_RECEIVED
}
