package com.waqiti.payment.cash.dto;

import lombok.Getter;

/**
 * Cash deposit status enumeration
 */
@Getter
public enum CashDepositStatus {
    
    PENDING("pending", "Reference Generated", "Waiting for cash to be deposited at location"),
    INITIATED("initiated", "Deposit Initiated", "Customer has initiated deposit at location"),
    PROCESSING("processing", "Processing", "Deposit is being processed by network"),
    COMPLETED("completed", "Completed", "Funds successfully deposited to wallet"),
    FAILED("failed", "Failed", "Deposit failed - funds not transferred"),
    CANCELLED("cancelled", "Cancelled", "Deposit cancelled by user or system"),
    EXPIRED("expired", "Expired", "Reference expired before deposit was made"),
    REFUNDED("refunded", "Refunded", "Deposit was refunded due to issues"),
    UNDER_REVIEW("under_review", "Under Review", "Deposit is under manual review");

    private final String code;
    private final String displayName;
    private final String description;

    CashDepositStatus(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || 
               this == EXPIRED || this == REFUNDED;
    }

    public boolean canCancel() {
        return this == PENDING || this == INITIATED;
    }

    public static CashDepositStatus fromCode(String code) {
        for (CashDepositStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown cash deposit status: " + code);
    }
}