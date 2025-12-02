package com.waqiti.billpayment.entity;

/**
 * Status values for bill share requests
 */
public enum ShareStatus {
    /**
     * Share request sent, awaiting response
     */
    PENDING,

    /**
     * Share request accepted by participant
     */
    ACCEPTED,

    /**
     * Share request rejected by participant
     */
    REJECTED,

    /**
     * Participant has paid their share
     */
    PAID,

    /**
     * Share request expired (not responded to in time)
     */
    EXPIRED,

    /**
     * Share request cancelled by creator
     */
    CANCELLED
}
