package com.waqiti.investment.tax.enums;

/**
 * Tax Document Filing Status
 * Tracks the complete lifecycle from generation to delivery
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum FilingStatus {
    /**
     * Document generation has been queued
     */
    PENDING_GENERATION,

    /**
     * Document has been generated and awaiting review
     */
    GENERATED,

    /**
     * Document is under compliance review
     */
    PENDING_REVIEW,

    /**
     * Document has passed compliance review
     */
    REVIEWED,

    /**
     * Document is queued for IRS FIRE submission
     */
    PENDING_IRS_FILING,

    /**
     * Document has been successfully filed with IRS via FIRE
     */
    FILED_WITH_IRS,

    /**
     * Document is ready for delivery to recipient
     */
    PENDING_RECIPIENT_DELIVERY,

    /**
     * Document has been delivered to recipient
     */
    DELIVERED_TO_RECIPIENT,

    /**
     * Full lifecycle complete
     */
    COMPLETED,

    /**
     * Document generation or filing failed
     */
    FAILED,

    /**
     * Document has been corrected (supersedes original)
     */
    CORRECTED
}
