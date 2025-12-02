package com.waqiti.billpayment.entity;

/**
 * Status values for biller entities
 */
public enum BillerStatus {
    /**
     * Biller is active and accepting payments
     */
    ACTIVE,

    /**
     * Biller is temporarily suspended (maintenance, technical issues)
     */
    SUSPENDED,

    /**
     * Biller is permanently inactive
     */
    INACTIVE,

    /**
     * Biller is under review (new biller onboarding)
     */
    UNDER_REVIEW,

    /**
     * Biller integration is being tested
     */
    TESTING
}
