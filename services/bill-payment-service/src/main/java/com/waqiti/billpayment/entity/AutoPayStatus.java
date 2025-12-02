package com.waqiti.billpayment.entity;

/**
 * Status values for auto-pay configurations
 */
public enum AutoPayStatus {
    /**
     * Auto-pay is active and will process payments
     */
    ACTIVE,

    /**
     * Auto-pay is paused by user (temporary)
     */
    PAUSED,

    /**
     * Auto-pay is suspended due to failures
     */
    SUSPENDED,

    /**
     * Auto-pay has been cancelled
     */
    CANCELLED,

    /**
     * Auto-pay is pending activation (awaiting verification)
     */
    PENDING
}
