package com.waqiti.billpayment.entity;

/**
 * Type of amount to pay for auto-pay configurations
 */
public enum AutoPayAmountType {
    /**
     * Pay the full bill amount
     */
    FULL_AMOUNT,

    /**
     * Pay only the minimum amount due
     */
    MINIMUM_DUE,

    /**
     * Pay a fixed amount each time
     */
    FIXED_AMOUNT
}
