package com.waqiti.investment.domain.enums;

public enum TransferType {
    DEPOSIT("Deposit"),
    WITHDRAWAL("Withdrawal"),
    DIVIDEND("Dividend"),
    FEE("Fee"),
    INTEREST("Interest");

    private final String displayName;

    TransferType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCredit() {
        return this == DEPOSIT || this == DIVIDEND || this == INTEREST;
    }

    public boolean isDebit() {
        return this == WITHDRAWAL || this == FEE;
    }
}