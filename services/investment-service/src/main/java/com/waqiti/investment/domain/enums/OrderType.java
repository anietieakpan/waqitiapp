package com.waqiti.investment.domain.enums;

public enum OrderType {
    MARKET("Market Order"),
    LIMIT("Limit Order"),
    STOP("Stop Order"),
    STOP_LIMIT("Stop Limit Order"),
    TRAILING_STOP("Trailing Stop Order"),
    MOC("Market on Close"),
    LOC("Limit on Close");

    private final String displayName;

    OrderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresPrice() {
        return this == LIMIT || this == STOP_LIMIT || this == LOC;
    }

    public boolean requiresStopPrice() {
        return this == STOP || this == STOP_LIMIT || this == TRAILING_STOP;
    }

    public boolean isMarketOrder() {
        return this == MARKET || this == MOC;
    }

    public boolean isConditional() {
        return this == STOP || this == STOP_LIMIT || this == TRAILING_STOP;
    }
}