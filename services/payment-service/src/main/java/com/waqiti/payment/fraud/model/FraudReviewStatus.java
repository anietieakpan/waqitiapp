package com.waqiti.payment.fraud.model;

/**
 * Fraud Review Case Status
 *
 * Represents the current state of a fraud review case.
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
public enum FraudReviewStatus {

    /**
     * Case is queued and waiting for analyst assignment
     */
    PENDING,

    /**
     * Case is currently being reviewed by an analyst
     */
    IN_REVIEW,

    /**
     * Review is complete and decision has been made
     */
    COMPLETED,

    /**
     * Case has been escalated to senior analyst
     */
    ESCALATED,

    /**
     * Case was cancelled (transaction cancelled, user withdrew, etc.)
     */
    CANCELLED,

    /**
     * Additional information requested from user
     */
    AWAITING_INFO,

    /**
     * Case is on hold pending external verification
     */
    ON_HOLD
}
