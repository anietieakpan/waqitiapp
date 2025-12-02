package com.waqiti.payment.model;

public enum BatchType {
    STANDARD,
    EXPRESS,
    BULK,
    PAYROLL,
    SETTLEMENT,
    MERCHANT_PAYOUT;

    public static BatchType fromString(String value) {
        if (value == null) return STANDARD;
        try {
            return BatchType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STANDARD;
        }
    }
}
