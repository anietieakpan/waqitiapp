package com.waqiti.investment.domain.enums;

public enum TimeInForce {
    DAY("Day Order"),
    GTC("Good Till Cancelled"),
    GTD("Good Till Date"),
    IOC("Immediate or Cancel"),
    FOK("Fill or Kill"),
    MOO("Market on Open"),
    MOC("Market on Close");

    private final String displayName;

    TimeInForce(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isImmediate() {
        return this == IOC || this == FOK;
    }

    public boolean requiresFullFill() {
        return this == FOK;
    }

    public boolean expiresEndOfDay() {
        return this == DAY || this == MOO || this == MOC;
    }

    public boolean isPersistent() {
        return this == GTC || this == GTD;
    }
}