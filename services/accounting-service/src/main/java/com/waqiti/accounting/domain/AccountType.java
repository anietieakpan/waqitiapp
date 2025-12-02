package com.waqiti.accounting.domain;

public enum AccountType {
    ASSET("Asset", NormalBalance.DEBIT),
    LIABILITY("Liability", NormalBalance.CREDIT),
    EQUITY("Equity", NormalBalance.CREDIT),
    REVENUE("Revenue", NormalBalance.CREDIT),
    EXPENSE("Expense", NormalBalance.DEBIT);

    private final String displayName;
    private final NormalBalance normalBalance;

    AccountType(String displayName, NormalBalance normalBalance) {
        this.displayName = displayName;
        this.normalBalance = normalBalance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NormalBalance getNormalBalance() {
        return normalBalance;
    }
}
