package com.waqiti.payment.fraud.model;

/**
 * Fraud Review Decision
 *
 * Final decision made by fraud analyst after review.
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
public enum FraudReviewDecision {

    /**
     * Transaction approved - legitimate transaction
     */
    APPROVE,

    /**
     * Transaction rejected - confirmed fraud
     */
    REJECT,

    /**
     * Case escalated to senior analyst for further review
     */
    ESCALATE,

    /**
     * Additional information required from user
     */
    REQUIRES_MORE_INFO,

    /**
     * Transaction approved with enhanced monitoring
     */
    APPROVE_WITH_MONITORING,

    /**
     * Transaction approved with restrictions (e.g., lower limits)
     */
    APPROVE_WITH_RESTRICTIONS
}
