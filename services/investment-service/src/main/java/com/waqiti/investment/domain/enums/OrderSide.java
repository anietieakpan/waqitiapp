package com.waqiti.investment.domain.enums;

public enum OrderSide {
    BUY("Buy"),
    SELL("Sell");

    private final String displayName;

    OrderSide(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBuy() {
        return this == BUY;
    }

    public boolean isSell() {
        return this == SELL;
    }

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}