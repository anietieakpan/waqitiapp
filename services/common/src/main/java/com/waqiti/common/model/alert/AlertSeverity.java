package com.waqiti.common.model.alert;

public enum AlertSeverity {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4),
    EMERGENCY(5);

    private final int level;

    AlertSeverity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static AlertSeverity fromString(String severity) {
        if (severity == null) {
            return MEDIUM;
        }
        try {
            return AlertSeverity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }

    public boolean isHigherThan(AlertSeverity other) {
        return this.level > other.level;
    }
}
